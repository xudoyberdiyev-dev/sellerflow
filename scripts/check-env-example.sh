#!/bin/sh
# .env.example — faqat shablon. Sirli kalitlar (TOKEN/PASSWORD/SECRET/PASSPHRASE/KEY, bootstrap email)
# bo'sh bo'lishi shart. CI va pre-commit shu skriptni ishga tushiradi.
set -eu
FILE="${1:-.env.example}"
[ -f "$FILE" ] || { echo "check-env-example: $FILE topilmadi" >&2; exit 2; }
bad=$(grep -nE '^[[:space:]]*[A-Z0-9_]*(TOKEN|PASSWORD|SECRET|PASSPHRASE|_KEY|BOOTSTRAP_EMAIL|RECOVERY_EMAIL)[A-Z0-9_]*[[:space:]]*=[[:space:]]*[^[:space:]#]' "$FILE" \
  | sed -E 's/=.*/=<yashirildi>/' || true)
if [ -n "$bad" ]; then
  echo "XATO: $FILE ichida sirli kalitlarga qiymat yozilgan (bo'sh qoldiring, haqiqiy qiymat .env ga):" >&2
  echo "$bad" >&2
  exit 1
fi
echo "check-env-example: OK ($FILE)"
