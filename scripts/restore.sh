#!/bin/bash
# Backupdan tiklash. Ishlatish:  ./scripts/restore.sh backups/sellerflow_YYYYMMDDTHHMMSSZ.dump[.gpg]
# DIQQAT: joriy bazadagi ma'lumotlar backupdagi bilan ALMASHTIRILADI.
# Xavfsizlik (M-18):
#  1. Tiklashdan oldin joriy bazaning snapshot'i olinadi: backups/pre-restore/… (olinmasa — to'xtaydi).
#  2. pg_restore --single-transaction --exit-on-error: xatoda hammasi qaytariladi, baza yarim holatda qolmaydi.
#  3. .gpg fayl gpg bilan ochiladi (parolni gpg so'raydi yoki BACKUP_ENCRYPTION_PASSPHRASE muhitdan).
set -euo pipefail
FILE="${1:?Backup fayl yolini bering}"
[ -f "$FILE" ] || { echo "Fayl topilmadi: $FILE"; exit 1; }
cd "$(dirname "$0")/.."

SRC="$FILE"
TMP=""
cleanup() { [ -n "$TMP" ] && rm -f "$TMP"; return 0; }
trap cleanup EXIT
if [[ "$FILE" == *.gpg ]]; then
  command -v gpg >/dev/null || { echo "gpg o'rnatilmagan: sudo apt install gnupg"; exit 1; }
  TMP="$(mktemp)"; chmod 600 "$TMP"
  if [ -n "${BACKUP_ENCRYPTION_PASSPHRASE:-}" ]; then
    gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 3 -d -o "$TMP" "$FILE" 3<<<"$BACKUP_ENCRYPTION_PASSPHRASE"
  else
    gpg --yes --quiet -d -o "$TMP" "$FILE"
  fi
  SRC="$TMP"
fi
# Backup yaroqliligini oldindan tekshirish (bazaga tegmaydi)
docker compose exec -T db pg_restore --list < "$SRC" > /dev/null || { echo "Backup fayli buzilgan yoki noto'g'ri formatda: $FILE"; exit 1; }

read -r -p "Joriy baza '$FILE' bilan almashtiriladi. Davom etasizmi? (yes/no) " ok
[ "$ok" = "yes" ] || { echo "Bekor qilindi."; exit 1; }

mkdir -p backups/pre-restore
SNAP="backups/pre-restore/sellerflow_pre-restore_$(date -u +%Y%m%dT%H%M%SZ).dump"
echo "Joriy baza snapshot'i: $SNAP"
( umask 077; docker compose exec -T db sh -c 'pg_dump -Fc -U "$POSTGRES_USER" sellerflow' > "$SNAP.tmp" )
[ -s "$SNAP.tmp" ] || { rm -f "$SNAP.tmp"; echo "Snapshot olinmadi — tiklash to'xtatildi."; exit 1; }
mv "$SNAP.tmp" "$SNAP"

docker compose stop app
if ! docker compose exec -T db sh -c \
    'pg_restore --clean --if-exists --no-owner --single-transaction --exit-on-error -U "$POSTGRES_USER" -d sellerflow' < "$SRC"; then
  echo "XATO: tiklash muvaffaqiyatsiz — tranzaksiya qaytarildi, baza o'zgarmadi."
  echo "Ilova qayta ishga tushirilmoqda. Snapshot: $SNAP"
  docker compose start app
  exit 1
fi
docker compose start app
echo "Tiklandi. Oldingi holat snapshot'i: $SNAP"
echo "Tekshirish: curl -s localhost:8080/actuator/health"
