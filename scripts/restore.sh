#!/bin/bash
# Backupdan tiklash. Ishlatish:  ./scripts/restore.sh backups/sellerflow_YYYYMMDDTHHMMSSZ.dump
# DIQQAT: joriy bazadagi ma'lumotlar backupdagi bilan ALMASHTIRILADI.
set -euo pipefail
FILE="${1:?Backup fayl yolini bering}"
[ -f "$FILE" ] || { echo "Fayl topilmadi: $FILE"; exit 1; }
read -r -p "Joriy baza '$FILE' bilan almashtiriladi. Davom etasizmi? (yes/no) " ok
[ "$ok" = "yes" ] || { echo "Bekor qilindi."; exit 1; }
docker compose stop app
docker compose exec -T db sh -c 'pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d sellerflow' < "$FILE"
docker compose start app
echo "Tiklandi. Tekshirish: curl -s localhost:8080/actuator/health"
