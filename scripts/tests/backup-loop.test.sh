#!/bin/bash
# backup-loop.sh regression testi (H-2/M-17). PG* muhit o'zgaruvchilari ishlaydigan bazaga qarashi kerak.
# Ishlatish: PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres PGDATABASE=sellerflow_test bash scripts/tests/backup-loop.test.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$(mktemp -d)"
trap 'rm -rf "${DIR}"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

# 10 ta eski (30 kunlik) backup
for i in $(seq -w 1 10); do
  f="${DIR}/sellerflow_202601${i}T000000Z.dump"; echo old > "$f"; touch -d '30 days ago' "$f"
done

# 1) Xato: hech narsa o'chirilmaydi, status.json da xato
if PGHOST=127.0.0.1 PGPORT=1 BACKUP_DIR="${DIR}" BACKUP_RUN_ONCE=1 bash "${HERE}/backup-loop.sh"; then fail "xato kutilgan edi"; fi
[ "$(ls "${DIR}"/sellerflow_*.dump | wc -l)" -eq 10 ] || fail "xatodan keyin backuplar o'chirildi"
grep -q '"lastError": "' "${DIR}/status.json" || fail "lastError yozilmadi"
grep -q '"lastSuccessAt": null' "${DIR}/status.json" || fail "lastSuccessAt null bo'lishi kerak"
grep -q '"consecutiveFailures": 1' "${DIR}/status.json" || fail "consecutiveFailures"

# 2) Muvaffaqiyat: yangi nusxa + eng yangi 6 ta eski (jami MIN_KEEP=7) qoladi, fayl huquqi 600
BACKUP_DIR="${DIR}" BACKUP_RUN_ONCE=1 bash "${HERE}/backup-loop.sh" || fail "backup muvaffaqiyatsiz"
[ "$(ls "${DIR}"/sellerflow_*.dump | wc -l)" -eq 7 ] || fail "rotatsiyadan keyin 7 ta nusxa kutilgan: $(ls "${DIR}")"
[ -f "${DIR}/sellerflow_20260110T000000Z.dump" ] || fail "eng yangi eski nusxa saqlanishi kerak"
[ ! -f "${DIR}/sellerflow_20260101T000000Z.dump" ] || fail "eng eski nusxa o'chirilishi kerak"
new="$(ls -1 "${DIR}"/sellerflow_2026*.dump | sort | tail -n1)"
[ "$(stat -c %a "${new}")" = "600" ] || fail "backup fayl huquqi 600 emas"
grep -q '"lastSuccessAt": "20' "${DIR}/status.json" || fail "lastSuccessAt yozilmadi"
grep -q '"consecutiveFailures": 0' "${DIR}/status.json" || fail "consecutiveFailures 0 bo'lishi kerak"

# 3) Keyingi xato oldingi muvaffaqiyat vaqtini saqlaydi
if PGHOST=127.0.0.1 PGPORT=1 BACKUP_DIR="${DIR}" BACKUP_RUN_ONCE=1 bash "${HERE}/backup-loop.sh"; then fail "xato kutilgan edi"; fi
grep -q '"lastSuccessAt": "20' "${DIR}/status.json" || fail "xatodan keyin lastSuccessAt yo'qoldi"
[ "$(ls "${DIR}"/sellerflow_*.dump | wc -l)" -eq 7 ] || fail "xatodan keyin nusxalar o'zgardi"

echo "backup-loop.test: OK"
