#!/bin/bash
# Tiklash sinovi (restore drill): oxirgi backupni VAQTINCHALIK bazaga tiklab, jadvallarni sanaydi.
# Ishlab turgan bazaga tegmaydi. Oyiga kamida bir marta bajarish tavsiya etiladi.
set -euo pipefail
FILE="${1:-$(ls -1t backups/sellerflow_*.dump backups/sellerflow_*.dump.gpg 2>/dev/null | head -1)}"
[ -n "$FILE" ] && [ -f "$FILE" ] || { echo "Backup topilmadi"; exit 1; }
echo "Sinov: $FILE"
TMP=""
trap '[ -n "$TMP" ] && rm -f "$TMP"; true' EXIT
if [[ "$FILE" == *.gpg ]]; then
  TMP="$(mktemp)"; chmod 600 "$TMP"
  if [ -n "${BACKUP_ENCRYPTION_PASSPHRASE:-}" ]; then
    gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 3 -d -o "$TMP" "$FILE" 3<<<"$BACKUP_ENCRYPTION_PASSPHRASE"
  else
    gpg --yes --quiet -d -o "$TMP" "$FILE"
  fi
  FILE="$TMP"
fi
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" --if-exists restore_drill && createdb -U "$POSTGRES_USER" restore_drill'
docker compose exec -T db sh -c 'pg_restore --no-owner --exit-on-error -U "$POSTGRES_USER" -d restore_drill' < "$FILE"
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d restore_drill -c "select (select count(*) from users) users, (select count(*) from businesses) businesses, (select count(*) from products) products, (select count(*) from calculation_history) history;"'
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" restore_drill'
echo "Restore drill OK"
