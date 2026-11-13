#!/bin/bash
# restore.sh regression testi (M-18, N-5) — "docker compose" soxta shim bilan lokal PostgreSQL'da.
# PG* muhit o'zgaruvchilari superuser ulanishiga qarashi kerak. "sellerflow*" bazalari yaratiladi/o'chiriladi.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"; for d in sellerflow sellerflow_prev sellerflow_restore sellerflow_failed; do dropdb --if-exists "$d" >/dev/null 2>&1 || true; done' EXIT
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

# Nusxa repo (restore.sh backups/ ni repo ildizida yaratadi; Flyway versiyasi migratsiya fayllaridan olinadi)
cp -r "${ROOT}/scripts" "${WORK}/repo-scripts"
mkdir -p "${WORK}/repo/src/main/resources/db/migration" && mv "${WORK}/repo-scripts" "${WORK}/repo/scripts"
touch "${WORK}/repo/src/main/resources/db/migration/V1__a.sql" "${WORK}/repo/src/main/resources/db/migration/V2__b.sql"
cd "${WORK}/repo"

for d in sellerflow sellerflow_prev sellerflow_restore; do dropdb --if-exists "$d"; done
createdb sellerflow
psql -q -d sellerflow -c "create table t(v int); insert into t values (1);
  create table flyway_schema_history(version varchar, success boolean); insert into flyway_schema_history values ('1', true);"
pg_dump -Fc -d sellerflow -f good.dump
psql -q -d sellerflow -c "insert into t values (2);"   # joriy holat: 2 qator

# 1) Buzilgan fayl: bazaga tegmaydi
echo "garbage" > bad.dump
if echo yes | ./scripts/restore.sh bad.dump; then fail "buzilgan fayl qabul qilindi"; fi
[ "$(psql -tA -d sellerflow -c 'select count(*) from t')" = "2" ] || fail "buzilgan fayl bazani o'zgartirdi"

# 2) Muvaffaqiyatli tiklash: snapshot olinadi, ma'lumot backupdagidek
echo yes | ./scripts/restore.sh good.dump || fail "tiklash muvaffaqiyatsiz"
[ "$(psql -tA -d sellerflow -c 'select count(*) from t')" = "1" ] || fail "tiklangan ma'lumot noto'g'ri"
snap="$(find backups/pre-restore -name '*.dump' | head -n1)"
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

# 4) N-5: joriy baza yangiroq sxemada (ortiqcha jadval) — tiklangandan keyin ortiqcha jadval qolmaydi
psql -q -d sellerflow -c "create table newer_v2(x int); insert into flyway_schema_history values ('2', true);"
echo yes | ./scripts/restore.sh good.dump || fail "eski sxemali backupni tiklash"
[ "$(psql -tA -d sellerflow -c "select count(*) from pg_tables where tablename = 'newer_v2'")" = "0" ] || fail "yangi sxemadagi jadval qoldi (Flyway ishga tushmaydi)"
[ "$(psql -tA -d sellerflow -c 'select max(version) from flyway_schema_history')" = "1" ] || fail "flyway tarixi backupdagidek emas"
[ "$(psql -tA -d sellerflow_prev -c "select count(*) from pg_tables where tablename = 'newer_v2'")" = "1" ] || fail "oldingi baza saqlanmadi"

# 5) --rollback: oldingi baza qaytadi
echo yes | ./scripts/restore.sh --rollback || fail "rollback"
[ "$(psql -tA -d sellerflow -c "select count(*) from pg_tables where tablename = 'newer_v2'")" = "1" ] || fail "rollback oldingi bazani qaytarmadi"

# 6) Backup koddan yangi (V9 > V2): to'xtaydi, joriy baza o'zgarmaydi, vaqtinchalik baza qolmaydi
psql -q -d sellerflow -c "insert into flyway_schema_history values ('9', true);"
pg_dump -Fc -d sellerflow -f future.dump
psql -q -d sellerflow -c "delete from flyway_schema_history where version = '9'; insert into t values (77);"
if echo yes | ./scripts/restore.sh future.dump; then fail "koddan yangi backup qabul qilindi"; fi
[ "$(psql -tA -d sellerflow -c 'select count(*) from t where v = 77')" = "1" ] || fail "joriy baza o'zgardi"
[ "$(psql -tA -d postgres -c "select count(*) from pg_database where datname = 'sellerflow_restore'")" = "0" ] || fail "vaqtinchalik baza qoldi"

# 7) Snapshot rotatsiyasi: ko'pi bilan 5 ta
[ "$(find backups/pre-restore -name 'sellerflow_pre-restore_*' | wc -l)" -le 5 ] || fail "snapshotlar rotatsiya qilinmadi"

echo "restore.test: OK"
