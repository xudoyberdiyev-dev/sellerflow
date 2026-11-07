#!/bin/bash
# restore.sh regression testi (M-18) — "docker compose" soxta shim bilan lokal PostgreSQL'da.
# PG* muhit o'zgaruvchilari superuser ulanishiga qarashi kerak. "sellerflow" bazasi yaratiladi/o'chiriladi.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"; dropdb --if-exists sellerflow >/dev/null 2>&1 || true' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

# Soxta docker: "compose exec -T db <cmd...>" -> lokal, "compose stop/start" -> log
mkdir -p "${WORK}/bin"
cat > "${WORK}/bin/docker" <<'SHIM'
#!/bin/bash
[ "$1" = "compose" ] || exit 99
shift
case "$1" in
  stop|start) echo "$1 $2" >> "${SHIM_LOG}"; exit 0 ;;
  exec) shift; [ "$1" = "-T" ] && shift; shift  # servis nomi
        export POSTGRES_USER="${PGUSER}"; exec "$@" ;;
esac
exit 98
SHIM
chmod +x "${WORK}/bin/docker"
export PATH="${WORK}/bin:${PATH}" SHIM_LOG="${WORK}/shim.log"

# Nusxa repo (restore.sh backups/ ni repo ildizida yaratadi)
cp -r "${ROOT}/scripts" "${WORK}/repo-scripts"
mkdir -p "${WORK}/repo" && mv "${WORK}/repo-scripts" "${WORK}/repo/scripts"
cd "${WORK}/repo"

dropdb --if-exists sellerflow; createdb sellerflow
psql -q -d sellerflow -c "create table t(v int); insert into t values (1);"
pg_dump -Fc -d sellerflow -f good.dump
psql -q -d sellerflow -c "insert into t values (2);"   # joriy holat: 2 qator

# 1) Buzilgan fayl: bazaga tegmaydi
echo "garbage" > bad.dump
if echo yes | ./scripts/restore.sh bad.dump; then fail "buzilgan fayl qabul qilindi"; fi
[ "$(psql -tA -d sellerflow -c 'select count(*) from t')" = "2" ] || fail "buzilgan fayl bazani o'zgartirdi"

# 2) Muvaffaqiyatli tiklash: snapshot olinadi, ma'lumot backupdagidek
echo yes | ./scripts/restore.sh good.dump || fail "tiklash muvaffaqiyatsiz"
[ "$(psql -tA -d sellerflow -c 'select count(*) from t')" = "1" ] || fail "tiklangan ma'lumot noto'g'ri"
snap="$(ls -1 backups/pre-restore/*.dump | head -n1)"
[ -n "${snap}" ] || fail "snapshot olinmadi"
pg_restore --list "${snap}" | grep -q "TABLE DATA public t" || fail "snapshot yaroqsiz"
[ "$(stat -c %a "${snap}")" = "600" ] || fail "snapshot huquqi 600 emas"
grep -q "start app" "${SHIM_LOG}" || fail "ilova qayta ishga tushirilmadi"

# 3) Shifrlangan backup
if command -v gpg >/dev/null 2>&1; then
  GNUPGHOME="$(mktemp -d)" gpg --batch --quiet --pinentry-mode loopback --passphrase "p-123456789012" --symmetric -o good.dump.gpg good.dump
  psql -q -d sellerflow -c "insert into t values (3);"
  echo yes | BACKUP_ENCRYPTION_PASSPHRASE="p-123456789012" GNUPGHOME="$(mktemp -d)" ./scripts/restore.sh good.dump.gpg || fail "gpg tiklash"
  [ "$(psql -tA -d sellerflow -c 'select count(*) from t')" = "1" ] || fail "gpg tiklangan ma'lumot noto'g'ri"
fi

# 4) Tiklash o'rtasida xato (single-transaction): baza o'zgarmaydi
pg_dump -Fc -d sellerflow -f conflict.dump
psql -q -d sellerflow -c "drop table t; create view t as select 42 as v;"   # --clean 'drop table t' xato beradi
if echo yes | ./scripts/restore.sh conflict.dump; then fail "xato kutilgan edi"; fi
[ "$(psql -tA -d sellerflow -c 'select v from t')" = "42" ] || fail "xatodan keyin baza yarim holatda qoldi"

echo "restore.test: OK"
