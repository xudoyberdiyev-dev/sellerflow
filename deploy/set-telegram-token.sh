#!/bin/bash
# Bot tokenini xavfsiz kiritish: ekranda ko'rinmaydi, faqat /opt/sellerflow/.env ga (chmod 600) yoziladi, keyin ilova ishga tushadi.
# Ishlatish (serverda, masalan Google Cloud konsolidagi "SSH" tugmasi orqali): sudo bash /opt/sellerflow/deploy/set-telegram-token.sh
set -euo pipefail
DIR=/opt/sellerflow
cd "$DIR"
[ -f .env ] || { echo "XATO: $DIR/.env yo'q — avval deploy/bootstrap.sh"; exit 1; }

read -r -s -p "Telegram bot tokeni (@BotFather): " TOKEN; echo
TOKEN="$(printf '%s' "$TOKEN" | tr -d '[:space:]')"
if ! printf '%s' "$TOKEN" | grep -qE '^[0-9]{5,}:[A-Za-z0-9_-]{30,}$'; then
  echo "XATO: token formati noto'g'ri (masalan 123456789:AA...)."; exit 1
fi
# Telegram'da tekshirish (javobdan faqat bot nomi chiqariladi)
NAME=$(printf 'url = "https://api.telegram.org/bot%s/getMe"\n' "$TOKEN" | curl -fs -m 10 -K - | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["result"]["username"] if d.get("ok") else "")' 2>/dev/null || true)
[ -n "$NAME" ] || { echo "XATO: Telegram tokenni qabul qilmadi (bekor qilinganmi?)."; exit 1; }

python3 - "$TOKEN" <<'PY'
import sys,re
t=sys.argv[1]; p='.env'; s=open(p).read()
s,n=re.subn(r'(?m)^TELEGRAM_BOT_TOKEN=.*$', lambda m: 'TELEGRAM_BOT_TOKEN='+t, s)
if n==0: s+='\nTELEGRAM_BOT_TOKEN='+t+'\n'
open(p,'w').write(s)
PY
chmod 600 .env
unset TOKEN
echo "✅ Token qabul qilindi: @${NAME}. Ilova ishga tushirilmoqda (birinchi marta 5–10 daqiqa)..."
chown sellerflow:sellerflow .env
sudo -u sellerflow docker compose up -d --build
echo "Tayyor. Tekshirish: sudo bash $DIR/deploy/server-check.sh"
