#!/bin/bash
# Tiklash sinovi (restore drill): oxirgi backupni VAQTINCHALIK bazaga tiklab, jadvallarni sanaydi.
# Ishlab turgan bazaga tegmaydi. Oyiga kamida bir marta bajarish tavsiya etiladi.
set -euo pipefail
# N-4: glob mos kelmasa ls xato bermasin (pipefail bilan skript jim exit 2 qilardi) — nullglob
shopt -s nullglob
candidates=(backups/sellerflow_*.dump backups/sellerflow_*.dump.gpg)
FILE="${1:-}"
if [ -z "$FILE" ] && [ "${#candidates[@]}" -gt 0 ]; then
  FILE="$(ls -1t -- "${candidates[@]}" | head -n 1)"
fi
[ -n "$FILE" ] && [ -f "$FILE" ] || { echo "Backup topilmadi (backups/sellerflow_*.dump[.gpg])" >&2; exit 1; }
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
