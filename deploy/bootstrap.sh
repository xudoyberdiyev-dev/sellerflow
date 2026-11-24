#!/bin/bash
# SellerFlow — yangi serverni bir martada o'rnatish (Google Cloud startup-script yoki qo'lda: sudo bash deploy/bootstrap.sh).
# Idempotent: qayta ishga tushirsa, mavjud .env va ma'lumotlarga tegmaydi.
#
# Nima qiladi:
#   1. Docker, swap, avtomatik xavfsizlik yangilanishlari, fail2ban.
#   2. Repo'ni /opt/sellerflow ga oladi (yoki yangilaydi).
#   3. .env yo'q bo'lsa — barcha sirlarni SERVERNING O'ZIDA tasodifiy yaratadi (tarmoq orqali hech qayerga yuborilmaydi):
#      DB_PASSWORD, SELLERFLOW_SECRETS_KEY, BACKUP_ENCRYPTION_PASSPHRASE, ADMIN_BOOTSTRAP_PASSWORD.
#      Nusxasi: /root/sellerflow-initial-secrets.txt (chmod 600) — parol menejeriga ko'chirib, faylni o'chiring.
#   4. Bot tokeni hali yo'q bo'lsa — ilovani ishga tushirmaydi; token: sudo bash /opt/sellerflow/deploy/set-telegram-token.sh
#
# Sozlamalar (ixtiyoriy) — muhit o'zgaruvchisi yoki GCE metadata atributi:
#   SF_ADMIN_EMAIL (admin-email), SF_ALERT_CHAT_ID (alert-chat-id), SF_REPO (repo-url), SF_BRANCH (repo-branch)
#
# Serial konsolga faqat "SELLERFLOW:" bilan boshlanadigan holat qatorlari yoziladi — sirlar YOZILMAYDI.
set -euo pipefail

say() { echo "SELLERFLOW: $*"; logger -t sellerflow "$*" 2>/dev/null || true; }

meta() {
  # GCE metadata (bo'lmasa — bo'sh)
  curl -fs -m 3 -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/attributes/$1" 2>/dev/null || true
}

ADMIN_EMAIL="${SF_ADMIN_EMAIL:-$(meta admin-email)}"
ALERT_CHAT_ID="${SF_ALERT_CHAT_ID:-$(meta alert-chat-id)}"
REPO="${SF_REPO:-$(meta repo-url)}"; REPO="${REPO:-https://github.com/xudoyberdiyev-dev/sellerflow.git}"
BRANCH="${SF_BRANCH:-$(meta repo-branch)}"; BRANCH="${BRANCH:-main}"
DIR=/opt/sellerflow
git() { command git -c safe.directory="$DIR" "$@"; }

say "bootstrap boshlandi"
export DEBIAN_FRONTEND=noninteractive
if ! command -v docker >/dev/null 2>&1; then
  say "paketlar o'rnatilmoqda"
  apt-get update -q
  apt-get install -y -q ca-certificates curl git gnupg python3 unattended-upgrades fail2ban openssl
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker fail2ban >/dev/null 2>&1 || true
dpkg-reconfigure -f noninteractive unattended-upgrades >/dev/null 2>&1 || true

# RAM 4 GB dan kam bo'lsa — 2 GB swap
if [ "$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)" -lt 3900 ] && ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  say "2 GB swap qo'shildi"
fi

id sellerflow >/dev/null 2>&1 || useradd -m -s /bin/bash -G docker sellerflow

if [ -d "$DIR/.git" ]; then
  git -C "$DIR" fetch -q origin "$BRANCH" && git -C "$DIR" merge -q --ff-only "origin/$BRANCH" || say "OGOHLANTIRISH: repo yangilanmadi (lokal o'zgarish?)"
else
  if ! git clone -q --branch "$BRANCH" "$REPO" "$DIR"; then
    say "XATO: repo olinmadi ($REPO). Repo private bo'lsa — docs/PRODUCTION_DEPLOYMENT.md (deploy key)"
    exit 1
  fi
fi
say "kod: $(git -C "$DIR" log --oneline -1)"

cd "$DIR"
if [ ! -f .env ]; then
  rnd() { openssl rand -base64 "$1" | tr -d '\n/+=' | head -c "$2"; }
  DBP=$(rnd 32 32); KEY=$(openssl rand -base64 32 | tr -d '\n'); BKP=$(rnd 32 32); ADP=$(rnd 32 24)
  cp .env.example .env
  set_env() { # KEY VALUE — mavjud qatorni almashtiradi
    python3 - "$1" "$2" <<'PY'
import sys,re
k,v=sys.argv[1],sys.argv[2]
p='.env'; s=open(p).read()
s,n=re.subn(r'(?m)^'+re.escape(k)+r'=.*$', lambda m: k+'='+v, s)
if n==0: s+='\n'+k+'='+v+'\n'
open(p,'w').write(s)
PY
  }
  set_env DB_PASSWORD "$DBP"
  set_env SELLERFLOW_SECRETS_KEY "$KEY"
  set_env BACKUP_ENCRYPTION_PASSPHRASE "$BKP"
  [ -n "$ADMIN_EMAIL" ] && set_env ADMIN_BOOTSTRAP_EMAIL "$ADMIN_EMAIL" && set_env ADMIN_BOOTSTRAP_PASSWORD "$ADP"
  [ -n "$ALERT_CHAT_ID" ] && set_env ALERT_TELEGRAM_CHAT_ID "$ALERT_CHAT_ID"
  chmod 600 .env
  umask 077
  cat > /root/sellerflow-initial-secrets.txt <<EOF
SellerFlow — dastlabki sirlar ($(date -u +%F)). PAROL MENEJERIGA ko'chiring, so'ng: sudo shred -u /root/sellerflow-initial-secrets.txt
SELLERFLOW_SECRETS_KEY=$KEY
BACKUP_ENCRYPTION_PASSPHRASE=$BKP
DB_PASSWORD=$DBP
ADMIN_BOOTSTRAP_EMAIL=$ADMIN_EMAIL
ADMIN_BOOTSTRAP_PASSWORD=$ADP
EOF
  say ".env yaratildi (sirlar serverda tasodifiy; nusxa: /root/sellerflow-initial-secrets.txt)"
fi
chown -R sellerflow:sellerflow "$DIR"

if grep -qE '^TELEGRAM_BOT_TOKEN=.+$' .env || [ -s secrets/TELEGRAM_BOT_TOKEN ]; then
  say "ilova ishga tushirilmoqda (birinchi marta 5–10 daqiqa)"
  # shellcheck disable=SC2024 # log root nomidan yoziladi — ataylab
  sudo -u sellerflow docker compose up -d --build >/var/log/sellerflow-compose.log 2>&1 \
    && say "compose up: OK" || { say "XATO: compose up (/var/log/sellerflow-compose.log)"; exit 1; }
else
  say "KUTILMOQDA: bot tokeni — sudo bash $DIR/deploy/set-telegram-token.sh"
fi
say "bootstrap tugadi"
