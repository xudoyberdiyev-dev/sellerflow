# SellerFlow — Production deploy qo'llanmasi

**Sana:** 2026-11-23 · Maqsad: bitta VPS'da yopiq beta (3–5 sotuvchi). Xavfsizlik: `docs/PRODUCTION_SECURITY.md`.

> **Production va development qat'iy ajratiladi:**
> - Production `.env` faqat serverda turadi.
> - Bot tokeni production va lokal muhitlarda **turli** bo'ladi (alohida test bot oching). Bitta token bilan ikki nusxa ishlasa, logda `HTTP 409` chiqadi.
> - Production bazasi va backup'lari lokal kompyuterga ko'chirilmaydi.
> - `BACKUP_ALLOW_PLAINTEXT=true` faqat lokalda.

## 1. Talablar
- **VPS:** 4 GB RAM / 2 vCPU / 40 GB SSD tavsiya (minimum 2 GB + 2 GB swap), Ubuntu 22.04 yoki 24.04. Hosting O'zbekistonda bo'lishi tavsiya etiladi (Uzum WAF, ma'lumot joylashuvi).
- SSH kalit bilan kirish (parol bilan kirish o'chirilgan).
- **Ixtiyoriy:** admin panel uchun domen (masalan, `admin.example.uz`).
- Telegram: @BotFather'dan **production** bot tokeni va alert'lar uchun chat ID (o'zingiz yoki operatorlar guruhi).

## 1a. Tezkor yo'l: Google Cloud + bootstrap (sirlar serverning o'zida yaratiladi)
1. Google Cloud'da loyiha va billing (yangi akkauntga 300 dollar kredit). VM: **e2-medium** (2 vCPU, 4 GB), Ubuntu 24.04, disk 40 GB.
   Kiruvchi firewall: faqat SSH (standart).
2. VM metadata'siga:
   - `startup-script` = `deploy/bootstrap.sh` mazmuni (yoki `curl -fsSL https://raw.githubusercontent.com/xudoyberdiyev-dev/sellerflow/main/deploy/bootstrap.sh | bash`);
   - `admin-email` = admin email;
   - `alert-chat-id` = alert chat ID.
3. Bootstrap o'zi bajaradi: Docker o'rnatish, repo'ni olish, `.env` dagi barcha sirlarni tasodifiy yaratish. Holat serial konsolda `SELLERFLOW:` qatorlari sifatida ko'rinadi, sirlar u yerga yozilmaydi.
4. Konsoldagi **SSH** tugmasi (brauzerda):
   ```bash
   sudo cat /root/sellerflow-initial-secrets.txt       # parol menejeriga ko'chiring, keyin:
   sudo shred -u /root/sellerflow-initial-secrets.txt
   sudo bash /opt/sellerflow/deploy/set-telegram-token.sh   # token ekranda ko'rinmaydi, Telegram'da tekshiriladi
   sudo bash /opt/sellerflow/deploy/server-check.sh --alert # natijani (sirlarsiz) Claude'ga yuborish mumkin
   ```
Keyingi qadamlar: 5-bo'lim (admin panel), 6-bo'lim (server tashqarisiga backup).

## 2. Serverni tayyorlash (bir marta)
```bash
ssh root@SERVER
git clone https://github.com/xudoyberdiyev-dev/sellerflow.git /opt/sellerflow
cd /opt/sellerflow
less deploy/server-setup.sh          # avval o'qing
WITH_PROXY=false bash deploy/server-setup.sh   # HTTPS proxy kerak bo'lsa: WITH_PROXY=true
chown -R sellerflow:sellerflow /opt/sellerflow
su - sellerflow && cd /opt/sellerflow
```
Skript nimalar qiladi:
- Docker o'rnatadi;
- firewall: kiruvchi faqat SSH (proxy bilan yana 80/443). Bot long polling ishlatgani uchun kiruvchi port shart emas;
- fail2ban va avtomatik xavfsizlik yangilanishlarini yoqadi;
- 2 GB swap qo'shadi;
- `sellerflow` foydalanuvchisini yaratadi.

## 3. `.env` (faqat serverda, `chmod 600`)
```bash
cp .env.example .env && chmod 600 .env && nano .env
```
| O'zgaruvchi | Qiymat | Majburiy |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | production bot tokeni | ha (yoki secrets) |
| `DB_PASSWORD` | `openssl rand -base64 24` (yangi) | ha |
| `SELLERFLOW_SECRETS_KEY` | `openssl rand -base64 32` — **parol menejerida ham saqlang** | ha (production) |
| `BACKUP_ENCRYPTION_PASSPHRASE` | `openssl rand -base64 24` — parol menejerida saqlang | ha |
| `ALERT_TELEGRAM_CHAT_ID` | operator chat ID | ha (production) |
| `ADMIN_BOOTSTRAP_EMAIL` / `_PASSWORD` | birinchi SUPERADMIN (12+ belgi) | faqat birinchi start; keyin **o'chiring** |
| `ADMIN_RECOVERY_EMAIL` / `_PASSWORD` | faqat avariya holatida (break-glass) | yo'q |
| `SUPPORT_LABEL` / `SUPPORT_URL` | botdagi support kontakti | tavsiya |
| `TERMS_VERSION` | shartlar versiyasi (yurist tasdiqlagan matn bilan oshiriladi) | standart bor |
| `DELETION_PURGE_DATA` | yurist qaroriga qadar `false` | yo'q |
| `BACKUP_KEEP_DAYS` | 14 | yo'q |
| `BACKUP_ALLOW_PLAINTEXT` | **bo'sh yoki `false`** | — |
| HTTPS uchun: `ADMIN_DOMAIN`, `ACME_EMAIL` | `admin.example.uz`, sizning email | proxy bilan |

**Ixtiyoriy — Docker secrets** (sirlar `docker inspect` da ko'rinmaydi): `docker-compose.secrets.yml` sarlavhasidagi yo'riqnoma. Bu rejimda `.env` dagi `TELEGRAM_BOT_TOKEN` va `SELLERFLOW_SECRETS_KEY` bo'sh qoladi.

## 4. Ishga tushirish
```bash
docker compose up -d --build                         # birinchi marta 5–10 daqiqa
# HTTPS proxy bilan:
docker compose -f docker-compose.yml -f docker-compose.proxy.yml up -d --build
# Docker secrets bilan: -f docker-compose.secrets.yml qo'shiladi
```

**Tekshirish (hammasi bajarilishi shart):**
```bash
docker compose ps                                   # db, app, backup — healthy (backup ~15 daqiqadan keyin)
docker compose logs --tail 100 app | grep -E "Successfully applied|Started SellerFlow|long polling started|WARN|ERROR"
curl -s http://127.0.0.1:8080/actuator/health/container   # {"status":"UP"...}
ls -la backups/ && cat backups/status.json          # birinchi backup .dump.gpg va lastSuccessAt
```
- Logda `SELLERFLOW_SECRETS_KEY berilmagan` yoki `ALERT_TELEGRAM_CHAT_ID is not set` **bo'lmasligi kerak**.
- Telegram'da `/start` → ro'yxatdan o'tish → bitta foyda hisobi.

Birinchi admin kirishidan keyin `.env` dan `ADMIN_BOOTSTRAP_PASSWORD` ni o'chiring va `docker compose up -d app` qiling.

## 5. Admin panelga kirish
- **SSH tunnel (standart, eng xavfsiz):** o'z kompyuteringizda `ssh -L 8080:localhost:8080 sellerflow@SERVER` → http://localhost:8080/admin/
- **HTTPS (proxy bilan):** `https://ADMIN_DOMAIN/admin/` — Caddy sertifikatni o'zi oladi. `/actuator` tashqaridan yopiq.
- Birinchi kirishda 2FA QR chiqadi — authenticator ilovasiga skaner qiling.

## 6. Backup
- **Avtomatik:** har kuni `./backups/sellerflow_*.dump.gpg` (gpg AES256). 14 kun saqlanadi, eng yangi 7 tasi doim qoladi. Holati: `backups/status.json`, admin panel → Tizim holati.
- **Server tashqarisiga (majburiy, H-3):** rclone bilan bulutga.
  ```bash
  sudo apt install rclone && rclone config        # masalan, Backblaze B2 / S3 / Google Drive → nom: offsite
  crontab -e
  30 4 * * * cd /opt/sellerflow && rclone copy backups offsite:sellerflow-backups --include "sellerflow_*.dump.gpg" --max-age 48h >> /var/log/sellerflow-offsite.log 2>&1
  ```
  Bulutda ham fayllar shifrlangan. `BACKUP_ENCRYPTION_PASSPHRASE` ni serverdan tashqarida (parol menejerida) saqlang — usiz backup ochilmaydi.
- **Oylik restore drill** (ishlab turgan bazaga tegmaydi): `./scripts/restore-drill.sh`

## 7. Tiklash (restore)
```bash
./scripts/restore.sh backups/sellerflow_YYYYMMDDTHHMMSSZ.dump.gpg
```
Skript qanday ishlaydi:
1. Faylni tekshiradi.
2. Uni **vaqtinchalik** bazaga tiklaydi (ishlab turgan baza o'zgarmaydi).
3. Sxema versiyasini tekshiradi.
4. Joriy bazaning shifrlangan snapshot'ini oladi.
5. Ilovani to'xtatib, bazalarni almashtiradi va ilovani qayta ishga tushiradi.

Agar natija yoqmasa: `./scripts/restore.sh --rollback` — oldingi baza qaytadi.

## 8. Monitoring
- **Healthcheck:** `docker compose ps`. `unhealthy` bo'lsa, Docker konteynerni qayta ishga tushiradi (`restart: unless-stopped`).
- **Alert chat** (`ALERT_TELEGRAM_CHAT_ID`):
  - backup xatosi, eskirgan yoki yo'q backup (24 soatda bir marta);
  - Uzum kunlik sinxronida tizimli xato yoki WAF bloki.
- **Metrikalar:** `curl -s http://127.0.0.1:8080/actuator/prometheus` (faqat server ichidan).
- **Resurslar (M-2)** — disk va RAM uchun oddiy cron alert. Fayl: `/opt/sellerflow/deploy/resource-alert.sh` (o'zingiz yaratasiz):
  ```bash
  #!/bin/bash
  set -a; . /opt/sellerflow/.env; set +a
  DISK=$(df --output=pcent / | tail -1 | tr -dc 0-9); MEM=$(free | awk '/Mem:/{printf "%d", $3*100/$2}')
  if [ "$DISK" -ge 80 ] || [ "$MEM" -ge 90 ]; then
    curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" -d chat_id="${ALERT_TELEGRAM_CHAT_ID}" \
      -d text="SellerFlow server: disk ${DISK}%, RAM ${MEM}%" > /dev/null
  fi
  ```
  `chmod 700` qiling va crontab'ga qo'shing: `*/15 * * * * /opt/sellerflow/deploy/resource-alert.sh`
- **Loglar:** `docker compose logs --tail 200 app`. Rotatsiya: har servis 3 × 10 MB.

## 9. Yangilash (deploy)
```bash
cd /opt/sellerflow
git fetch && git log --oneline HEAD..origin/main      # nima kelayotganini ko'ring
docker compose exec -T backup bash -c 'BACKUP_RUN_ONCE=1 bash /backup-loop.sh'   # deploy oldidan qo'lda backup
git tag -f before-deploy-$(date +%Y%m%d%H%M) && git pull --ff-only
docker compose up -d --build
docker compose logs --tail 100 app | grep -E "Successfully applied|Started SellerFlow|ERROR"
```
Flyway yangi migratsiyalarni o'zi qo'llaydi. Ilova ishga tushmasa, 10-bo'lim.

## 10. Rollback
1. **Kodni qaytarish** (migratsiya bo'lmagan yoki orqaga mos deploy):
   ```bash
   git checkout before-deploy-YYYYMMDDHHMM && docker compose up -d --build
   ```
2. **Yangi migratsiya qo'llangan bo'lsa:** eski kod yangi sxemani tanimaydi. Shuning uchun deploy oldidan olingan backup'dan tiklang (7-bo'lim), keyin eski kodni ishga tushiring. `restore.sh` sxema versiyasini o'zi tekshiradi.
3. Keyin `git checkout main` — muammo tuzatilgach qayta deploy.

## 11. Tez-tez uchraydigan muammolar
| Belgi | Sabab va yechim |
|---|---|
| Logda `TELEGRAM_BOT_TOKEN .env faylida berilishi shart` | `.env` da yoki `secrets/` da token yo'q |
| `Telegram getUpdates failed: HTTP 401` | Token noto'g'ri yoki bekor qilingan |
| `HTTP 409` | Shu token bilan boshqa nusxa ishlayapti (lokal bot to'xtatilmagan) |
| Botda «Uzum serverimiz so'rovini blokladi» | Uzum WAF server IP'ni bloklagan — O'zbekistondagi hostingga ko'chiring |
| Admin login rad etiladi, logda «TOTP sirini ochib bo'lmadi» | `SELLERFLOW_SECRETS_KEY` almashgan: to'g'ri kalitni qaytaring yoki `ADMIN_RECOVERY_*` (`PRODUCTION_SECURITY.md` §2.1) |
| `backup` unhealthy | `docker compose logs backup`; `BACKUP_ENCRYPTION_PASSPHRASE` va diskni tekshiring |
| Port 8080 band | `docker-compose.yml` da `127.0.0.1:8081:8080` |
