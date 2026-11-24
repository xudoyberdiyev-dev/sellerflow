#!/bin/bash
# SellerFlow server tekshiruvi. Natijani Claude'ga yuborish xavfsiz: sirlar, tokenlar va sotuvchi ma'lumotlari CHIQARILMAYDI
# (faqat bor/yo'q, holat, son va vaqt).
# Ishlatish: sudo bash /opt/sellerflow/deploy/server-check.sh            — tekshiruv
#            sudo bash /opt/sellerflow/deploy/server-check.sh --alert    — alert chatga sinov xabari ham yuboriladi
#            sudo bash /opt/sellerflow/deploy/server-check.sh --drill    — backup'dan vaqtinchalik bazaga tiklash sinovi ham
set -uo pipefail
DIR=/opt/sellerflow
git() { command git -c safe.directory="$DIR" "$@"; }
cd "$DIR" 2>/dev/null || { echo "FAIL $DIR yo'q"; exit 1; }
PASS=0; FAIL=0; WARN=0
ok()   { echo "PASS  $*"; PASS=$((PASS+1)); }
bad()  { echo "FAIL  $*"; FAIL=$((FAIL+1)); }
warn() { echo "WARN  $*"; WARN=$((WARN+1)); }
envset() { grep -qE "^$1=.+$" .env 2>/dev/null || [ -s "secrets/$1" ]; }
envget() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2-; }
dc() { docker compose "$@"; }

echo "== SellerFlow server-check $(date -u +%FT%TZ) =="
echo "kod: $(git log --oneline -1 2>/dev/null)"
echo "server: $(nproc) vCPU, $(awk '/MemTotal/{printf "%.1f GB", $2/1048576}' /proc/meminfo) RAM, disk / $(df -h --output=pcent / | tail -1 | tr -d ' ') band, swap $(free -m | awk '/Swap/{print $2" MB"}')"

echo "-- .env (faqat bor/yo'q)"
[ "$(stat -c %a .env 2>/dev/null)" = "600" ] && ok ".env ruxsatlari 600" || bad ".env ruxsatlari 600 emas yoki fayl yo'q"
for k in TELEGRAM_BOT_TOKEN DB_PASSWORD SELLERFLOW_SECRETS_KEY BACKUP_ENCRYPTION_PASSPHRASE ALERT_TELEGRAM_CHAT_ID; do
  envset "$k" && ok "$k berilgan" || bad "$k berilmagan"
done
[ "$(envget BACKUP_ALLOW_PLAINTEXT)" = "true" ] && bad "BACKUP_ALLOW_PLAINTEXT=true (production'da taqiqlangan)" || ok "backup shifrlash majburiy"
envset ADMIN_BOOTSTRAP_PASSWORD && warn "ADMIN_BOOTSTRAP_PASSWORD hali .env da — birinchi kirishdan keyin o'chiring" || ok "bootstrap paroli .env da yo'q"
[ -f /root/sellerflow-initial-secrets.txt ] && warn "/root/sellerflow-initial-secrets.txt hali bor — parol menejeriga ko'chirib, o'chiring"

echo "-- konteynerlar"
for s in db app backup; do
  st=$(docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$(dc ps -q "$s" 2>/dev/null)" 2>/dev/null)
  case "$st" in running/healthy) ok "$s: $st";; running/starting) warn "$s: $st (kuting)";; *) bad "$s: ${st:-yo‘q}";; esac
done
h=$(curl -fs -m 5 http://127.0.0.1:8080/actuator/health/container 2>/dev/null)
echo "$h" | grep -q '"UP"' && ok "health: UP" || bad "health: ${h:-javob yo‘q}"

echo "-- ilova logi (oxirgi 2000 qator)"
LOG=$(dc logs --tail 2000 app 2>/dev/null)
echo "$LOG" | grep -q "Started SellerFlowApplication" && ok "ilova ishga tushgan" || bad "\"Started SellerFlowApplication\" yo'q"
echo "$LOG" | grep -q "Telegram long polling started" && ok "Telegram polling ishlayapti" || bad "Telegram polling boshlanmagan"
echo "$LOG" | grep -qE "getUpdates failed: HTTP 401" && bad "Telegram 401 — token noto'g'ri"
echo "$LOG" | grep -qE "getUpdates failed: HTTP 409" && bad "Telegram 409 — shu token bilan boshqa nusxa ishlayapti"
echo "$LOG" | grep -q "SELLERFLOW_SECRETS_KEY berilmagan" && bad "shifrlash kaliti yo'q (TOTP ochiq, Uzum o'chiq)"
echo "$LOG" | grep -q "TOTP sirini ochib bo'lmadi" && bad "TOTP siri ochilmadi — SELLERFLOW_SECRETS_KEY almashganmi?"
n=$(echo "$LOG" | grep -c " ERROR ")
[ "$n" -eq 0 ] && ok "logda ERROR yo'q" || warn "logda $n ta ERROR (docker compose logs app | grep ERROR)"

echo "-- baza"
DBU=$(envget DB_USER); DBU=${DBU:-sellerflow}
q() { dc exec -T db psql -U "$DBU" -d sellerflow -tAc "$1" 2>/dev/null | tr -d '[:space:]'; }
v=$(q "select max(version::int) from flyway_schema_history where success")
[ -n "$v" ] && ok "Flyway: V$v" || bad "flyway_schema_history o'qilmadi"
f=$(q "select count(*) from flyway_schema_history where not success")
[ "${f:-1}" = "0" ] && ok "muvaffaqiyatsiz migratsiya yo'q" || bad "muvaffaqiyatsiz migratsiya: $f"
echo "info  sotuvchilar: $(q "select count(*) from businesses"), mahsulotlar: $(q "select count(*) from products"), adminlar: $(q "select count(*) from admins"), 2FA ulangan: $(q "select count(*) from admins where totp_secret is not null")"
p=$(q "select count(*) from admins where totp_secret is not null and totp_secret not like 'enc:v1:%'")
[ "$p" = "0" ] && ok "TOTP sirlari shifrlangan" || bad "$p ta TOTP siri ochiq"

echo "-- Uzum"
c=$(q "select count(*) from marketplace_connections")
if [ "${c:-0}" = "0" ]; then echo "info  Uzum ulanishi yo'q"; else
  echo "info  ulanishlar: $c; holatlar: $(q "select string_agg(status||':'||coalesce(last_error,'-')||'/'||coalesce(finance_error,'-'),',') from marketplace_connections")"
  echo "info  oxirgi sinxron: $(dc exec -T db psql -U "$DBU" -d sellerflow -tAc "select max(last_sync_at) from marketplace_connections" 2>/dev/null | xargs)"
  echo "info  Uzum SKU: $(q "select count(*) from products where external_source='UZUM'"), sotuv qatorlari: $(q "select count(*) from marketplace_sales"), xarajatlar: $(q "select count(*) from marketplace_expenses")"
  w=$(q "select count(*) from marketplace_connections where last_error='WAF_BLOCKED' or finance_error='WAF_BLOCKED'")
  [ "${w:-0}" = "0" ] && ok "Uzum WAF bloki yo'q" || bad "Uzum WAF server IP'ni bloklagan — O'zbekistondagi hosting kerak"
fi
code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 https://api-seller.uzum.uz/api/seller-openapi/v1/shops)
case "$code" in 401|403) ct=$(curl -s -o /dev/null -w '%{content_type}' -m 10 https://api-seller.uzum.uz/api/seller-openapi/v1/shops)
  echo "$ct" | grep -qi html && bad "Uzum API: $code HTML — WAF bu IP'ni bloklayapti" || ok "Uzum API'ga yetib boriladi (kalitsiz $code — kutilgan)";;
  000) bad "Uzum API'ga ulanib bo'lmadi";; *) warn "Uzum API javobi: $code";; esac

echo "-- backup"
last=$(ls -1t backups/sellerflow_*.dump.gpg 2>/dev/null | head -1)
if [ -n "$last" ]; then
  age=$(( ( $(date +%s) - $(stat -c %Y "$last") ) / 3600 ))
  [ "$age" -le 26 ] && ok "oxirgi backup ${age} soat oldin ($(du -h "$last" | cut -f1))" || bad "oxirgi backup ${age} soat oldin (eskirgan)"
  head -c 5 "$last" | grep -q PGDMP && bad "backup SHIFRLANMAGAN" || ok "backup shifrlangan"
else bad "backup fayli yo'q (birinchi backup ~15 daqiqadan keyin)"; fi
[ -f backups/status.json ] && echo "info  status.json: $(tr -d '\n' < backups/status.json | head -c 300)"

if [ "${1:-}" = "--drill" ] && [ -n "${last:-}" ]; then
  echo "-- restore drill (ishlab turgan bazaga tegmaydi)"
  if BACKUP_ENCRYPTION_PASSPHRASE="$(envget BACKUP_ENCRYPTION_PASSPHRASE)" bash scripts/restore-drill.sh >/tmp/sf-drill.log 2>&1; then ok "restore drill: OK"; tail -5 /tmp/sf-drill.log | sed 's/^/info  /'
  else bad "restore drill xato"; tail -15 /tmp/sf-drill.log | sed 's/^/info  /'; fi
fi

if [ "${1:-}" = "--alert" ]; then
  T=$(envget TELEGRAM_BOT_TOKEN); [ -z "$T" ] && [ -s secrets/TELEGRAM_BOT_TOKEN ] && T=$(cat secrets/TELEGRAM_BOT_TOKEN)
  # token jarayonlar ro'yxatida ko'rinmasin — URL curl'ga stdin orqali (-K -)
  r=$(printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "$T" | curl -s -m 10 -K - \
      --data-urlencode "chat_id=$(envget ALERT_TELEGRAM_CHAT_ID)" \
      --data-urlencode "text=SellerFlow server-check: sinov xabari ($(hostname))" | grep -o '"ok":[a-z]*')
  unset T
  [ "$r" = '"ok":true' ] && ok "alert chatga sinov xabari yuborildi" || bad "alert yuborilmadi (chat ID yoki botni chatga qo'shish)"
fi

echo "-- xavfsizlik"
ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE '^(0\.0\.0\.0|\[::\]|\*):8080$' && bad "8080 tashqariga ochiq" || ok "8080 faqat 127.0.0.1"
ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ':5432$' && bad "PostgreSQL porti host'da ochiq" || ok "PostgreSQL tashqariga ochilmagan"
systemctl is-active --quiet fail2ban && ok "fail2ban faol" || warn "fail2ban faol emas"

echo "== NATIJA: PASS=$PASS WARN=$WARN FAIL=$FAIL =="
[ "$FAIL" -eq 0 ]
