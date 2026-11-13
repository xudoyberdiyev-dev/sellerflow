#!/bin/bash
# Backupdan tiklash.
#   ./scripts/restore.sh backups/sellerflow_YYYYMMDDTHHMMSSZ.dump[.gpg]   — tiklash
#   ./scripts/restore.sh --rollback                                       — oxirgi tiklashni bekor qilish
# DIQQAT: joriy bazadagi ma'lumotlar backupdagi bilan ALMASHTIRILADI.
#
# Qanday ishlaydi (M-18, N-5):
#  1. Backup oldindan tekshiriladi (pg_restore --list) va VAQTINCHALIK "sellerflow_restore" bazasiga tiklanadi —
#     ishlab turgan bazaga tegilmaydi. Xato bo'lsa — vaqtinchalik baza o'chiriladi, hech narsa o'zgarmaydi.
#  2. Backupdagi Flyway versiyasi koddagidan YANGI bo'lsa — to'xtaydi (ilova bunday bazada ishga tushmaydi).
#     Eskiroq bo'lsa — ilova ishga tushganda yetishmayotgan migratsiyalarni o'zi qo'llaydi (toza sxema).
#  3. Joriy baza snapshot'i: backups/pre-restore/… (passphrase bo'lsa gpg bilan shifrlanadi; eng yangi 5 tasi saqlanadi).
#  4. Ilova to'xtatiladi, bazalar almashtiriladi: sellerflow → sellerflow_prev, sellerflow_restore → sellerflow.
#     Eski sxemadagi ortiqcha jadvallar qolmaydi (oldingi --clean usulidagi muammo), eski baza --rollback uchun turadi.
set -euo pipefail
cd "$(dirname "$0")/.."

DB=sellerflow
psql_admin() { docker compose exec -T db sh -c "psql -v ON_ERROR_STOP=1 -q -U \"\$POSTGRES_USER\" -d postgres $1"; }

swap_back() {
  docker compose stop app
  psql_admin "-c \"select pg_terminate_backend(pid) from pg_stat_activity where datname in ('${DB}','${DB}_prev') and pid <> pg_backend_pid()\"" >/dev/null
  psql_admin "-c 'begin' -c 'alter database ${DB} rename to ${DB}_failed' -c 'alter database ${DB}_prev rename to ${DB}' -c 'commit'"
  psql_admin "-c 'drop database if exists ${DB}_failed'"
  docker compose start app
}

if [ "${1:-}" = "--rollback" ]; then
  exists="$(docker compose exec -T db sh -c "psql -tA -U \"\$POSTGRES_USER\" -d postgres -c \"select count(*) from pg_database where datname = '${DB}_prev'\"")"
  [ "${exists//[[:space:]]/}" = "1" ] || { echo "Qaytariladigan baza (${DB}_prev) yo'q."; exit 1; }
  read -r -p "Oxirgi tiklashdan oldingi baza qaytariladi. Davom etasizmi? (yes/no) " ok
  [ "$ok" = "yes" ] || { echo "Bekor qilindi."; exit 1; }
  swap_back
  echo "Qaytarildi: oldingi baza yana faol."
  exit 0
fi

FILE="${1:?Backup fayl yolini bering}"
[ -f "$FILE" ] || { echo "Fayl topilmadi: $FILE"; exit 1; }

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
docker compose exec -T db pg_restore --list < "$SRC" > /dev/null || { echo "Backup fayli buzilgan yoki noto'g'ri formatda: $FILE"; exit 1; }

read -r -p "Joriy baza '$FILE' bilan almashtiriladi. Davom etasizmi? (yes/no) " ok
[ "$ok" = "yes" ] || { echo "Bekor qilindi."; exit 1; }

# 1. Vaqtinchalik bazaga tiklash (ishlab turgan baza o'zgarmaydi)
psql_admin "-c 'drop database if exists ${DB}_restore' -c 'create database ${DB}_restore'"
if ! docker compose exec -T db sh -c \
    "pg_restore --no-owner --single-transaction --exit-on-error -U \"\$POSTGRES_USER\" -d ${DB}_restore" < "$SRC"; then
  psql_admin "-c 'drop database if exists ${DB}_restore'" || true
  echo "XATO: backupni tiklab bo'lmadi — joriy baza o'zgarmadi."
  exit 1
fi

# 2. Flyway versiyasi: backup koddan yangi bo'lmasligi kerak
code_ver="$(find src/main/resources/db/migration -name 'V*__*.sql' 2>/dev/null | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -1)"
dump_ver="$(docker compose exec -T db sh -c "psql -tA -U \"\$POSTGRES_USER\" -d ${DB}_restore -c \"select coalesce(max(version::int), 0) from flyway_schema_history where success and version ~ '^[0-9]+\$'\"" 2>/dev/null || echo 0)"
dump_ver="${dump_ver//[[:space:]]/}"
if [ -n "$code_ver" ] && [ -n "$dump_ver" ] && [ "$dump_ver" -gt "$code_ver" ]; then
  psql_admin "-c 'drop database if exists ${DB}_restore'" || true
  echo "XATO: backup sxemasi (V${dump_ver}) koddagidan (V${code_ver}) yangi — avval ilovaning mos versiyasini o'rnating."
  exit 1
fi
[ -n "$dump_ver" ] && [ -n "$code_ver" ] && [ "$dump_ver" -lt "$code_ver" ] \
  && echo "Eslatma: backup V${dump_ver}, kod V${code_ver} — ilova ishga tushganda migratsiyalarni qo'llaydi."

# 3. Joriy baza snapshot'i
mkdir -p backups/pre-restore
SNAP="backups/pre-restore/${DB}_pre-restore_$(date -u +%Y%m%dT%H%M%SZ).dump"
( umask 077; docker compose exec -T db sh -c "pg_dump -Fc -U \"\$POSTGRES_USER\" ${DB}" > "$SNAP.tmp" )
[ -s "$SNAP.tmp" ] || { rm -f "$SNAP.tmp"; psql_admin "-c 'drop database if exists ${DB}_restore'" || true; echo "Snapshot olinmadi — tiklash to'xtatildi."; exit 1; }
if [ -n "${BACKUP_ENCRYPTION_PASSPHRASE:-}" ] && command -v gpg >/dev/null; then
  ( umask 077; gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 3 --symmetric --cipher-algo AES256 \
      -o "$SNAP.gpg" "$SNAP.tmp" 3<<<"$BACKUP_ENCRYPTION_PASSPHRASE" )
  rm -f "$SNAP.tmp"; SNAP="$SNAP.gpg"
else
  mv "$SNAP.tmp" "$SNAP"
fi
chmod 600 "$SNAP"
echo "Joriy baza snapshot'i: $SNAP"
# eng yangi 5 ta snapshot saqlanadi
ls -1t backups/pre-restore/${DB}_pre-restore_* 2>/dev/null | tail -n +6 | while read -r old; do rm -f -- "$old"; done

# 4. Almashtirish
docker compose stop app
psql_admin "-c \"select pg_terminate_backend(pid) from pg_stat_activity where datname = '${DB}' and pid <> pg_backend_pid()\"" >/dev/null
psql_admin "-c 'drop database if exists ${DB}_prev'"
if ! psql_admin "-c 'begin' -c 'alter database ${DB} rename to ${DB}_prev' -c 'alter database ${DB}_restore rename to ${DB}' -c 'commit'"; then
  echo "XATO: bazalarni almashtirib bo'lmadi (boshqa ulanish bor?) — joriy baza o'zgarmadi."
  docker compose start app
  exit 1
fi
docker compose start app
echo "Tiklandi. Oldingi baza '${DB}_prev' sifatida saqlandi; snapshot: $SNAP"
echo "Bekor qilish: ./scripts/restore.sh --rollback"
echo "Tekshirish: curl -s localhost:8080/actuator/health"
