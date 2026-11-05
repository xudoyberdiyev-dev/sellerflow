#!/bin/bash
# Docker "backup" servisi ichida ishlaydi: har 24 soatda pg_dump, eski fayllarni o'chiradi.
set -u
mkdir -p /backups
while true; do
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  file="/backups/sellerflow_${ts}.dump"
  if pg_dump -Fc -f "${file}.tmp" && mv "${file}.tmp" "${file}"; then
    echo "[backup] OK ${file} ($(du -h "${file}" | cut -f1))"
  else
    rm -f "${file}.tmp"
    echo "[backup] FAILED at ${ts}" >&2
  fi
  find /backups -name 'sellerflow_*.dump' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete
  sleep 86400
done
