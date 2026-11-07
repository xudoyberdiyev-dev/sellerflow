#!/bin/bash
# Docker "backup" servisi ichida ishlaydi: har BACKUP_INTERVAL_SECONDS (standart 24 soat) da pg_dump.
# - Rotatsiya FAQAT muvaffaqiyatli backupdan keyin; eng yangi BACKUP_MIN_KEEP (standart 7) nusxa hech qachon o'chirilmaydi.
# - Holat: ${BACKUP_DIR}/status.json (lastSuccessAt, lastSuccessFile, lastFailureAt, lastError, consecutiveFailures).
#   Ilova shu faylni o'qiydi (health + Telegram alert), compose healthcheck ham shundan foydalanadi.
# - BACKUP_ENCRYPTION_PASSPHRASE berilsa: nusxa gpg (AES256, simmetrik) bilan shifrlanadi -> sellerflow_*.dump.gpg.
#   gpg topilmasa backup XATO hisoblanadi (shifrlanmagan nusxa jim yozilmaydi). Parol process ro'yxatida ko'rinmaydi (fd 3).
# - BACKUP_RUN_ONCE=1 — bitta backup va chiqish (test/qo'lda ishga tushirish uchun).
set -u

BACKUP_DIR="${BACKUP_DIR:-/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
MIN_KEEP="${BACKUP_MIN_KEEP:-7}"
INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"
STATUS="${BACKUP_DIR}/status.json"

mkdir -p "${BACKUP_DIR}"
umask 077

json_field() { # status.json dan oddiy satr maydonini o'qish (jq'siz)
  [ -f "${STATUS}" ] || return 0
  sed -n "s/.*\"$1\": *\"\\([^\"]*\\)\".*/\\1/p" "${STATUS}" | head -n1
}
json_str() { # null yoki qochirilgan JSON satri
  if [ -z "$1" ]; then printf 'null'; else printf '"%s"' "$(printf '%s' "$1" | tr -d '\000-\037' | sed 's/\\/\\\\/g; s/"/\\"/g')"; fi
}

last_success_at="$(json_field lastSuccessAt)"
last_success_file="$(json_field lastSuccessFile)"
last_failure_at="$(json_field lastFailureAt)"
last_error="$(json_field lastError)"
failures="$(sed -n 's/.*"consecutiveFailures": *\([0-9][0-9]*\).*/\1/p' "${STATUS}" 2>/dev/null | head -n1)"
failures="${failures:-0}"

write_status() {
  local tmp="${STATUS}.tmp"
  {
    printf '{\n'
    printf '  "lastSuccessAt": %s,\n' "$(json_str "${last_success_at}")"
    printf '  "lastSuccessFile": %s,\n' "$(json_str "${last_success_file}")"
    printf '  "lastFailureAt": %s,\n' "$(json_str "${last_failure_at}")"
    printf '  "lastError": %s,\n' "$(json_str "${last_error}")"
    printf '  "consecutiveFailures": %d,\n' "${failures}"
    printf '  "updatedAt": %s\n' "$(json_str "$(date -u +%Y-%m-%dT%H:%M:%SZ)")"
    printf '}\n'
  } > "${tmp}" && chmod 644 "${tmp}" && mv "${tmp}" "${STATUS}"
}

rotate() {
  # Eng yangi MIN_KEEP ta nusxa daxlsiz; qolganlaridan KEEP_DAYS kundan eskilari o'chiriladi.
  local i=0 f
  while IFS= read -r f; do
    i=$((i + 1))
    [ "${i}" -le "${MIN_KEEP}" ] && continue
    if [ -n "$(find "${f}" -maxdepth 0 -mtime +"${KEEP_DAYS}" 2>/dev/null)" ]; then
      rm -f -- "${f}" && echo "[backup] rotated ${f}"
    fi
  done < <(for f in "${BACKUP_DIR}"/sellerflow_*.dump "${BACKUP_DIR}"/sellerflow_*.dump.gpg; do
             [ -f "${f}" ] && printf '%s\n' "${f}"
           done | sort -r)
}

run_backup() {
  local ts file err
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  file="${BACKUP_DIR}/sellerflow_${ts}.dump"
  err="$(pg_dump -Fc -f "${file}.tmp" 2>&1 >/dev/null)"
  local rc=$?
  if [ ${rc} -eq 0 ] && [ -n "${BACKUP_ENCRYPTION_PASSPHRASE:-}" ]; then
    if ! command -v gpg >/dev/null 2>&1; then
      err="BACKUP_ENCRYPTION_PASSPHRASE berilgan, lekin gpg topilmadi"; rc=1
    else
      err="$(gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 3 --symmetric --cipher-algo AES256 \
              -o "${file}.gpg.tmp" "${file}.tmp" 3<<<"${BACKUP_ENCRYPTION_PASSPHRASE}" 2>&1)"; rc=$?
      rm -f "${file}.tmp"
      if [ ${rc} -eq 0 ]; then mv "${file}.gpg.tmp" "${file}.tmp"; else rm -f "${file}.gpg.tmp"; fi
      file="${file}.gpg"
    fi
  fi
  if [ ${rc} -eq 0 ] && [ -s "${file%.gpg}.tmp" ] && mv "${file%.gpg}.tmp" "${file}"; then
    chmod 600 "${file}"
    failures=0
    last_success_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    last_success_file="$(basename "${file}")"
    echo "[backup] OK ${file} ($(du -h "${file}" | cut -f1))"
    write_status
    rotate
    return 0
  fi
  rm -f "${file}.tmp" "${file%.gpg}.tmp"
  failures=$((failures + 1))
  last_failure_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  last_error="$(printf '%s' "${err:-pg_dump failed}" | tail -n 3 | tr '\n' ' ' | cut -c1-300)"
  echo "[backup] FAILED at ${ts}: ${last_error}" >&2
  write_status
  return 1
}

if [ "${BACKUP_RUN_ONCE:-0}" = "1" ]; then
  run_backup
  exit $?
fi

while true; do
  run_backup
  sleep "${INTERVAL}"
done
