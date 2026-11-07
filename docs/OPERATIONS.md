# SellerFlow — ekspluatatsiya qo'llanmasi

## 1. Server talablari
- Linux VPS: kamida 1 vCPU va 2 GB RAM (yig'ish paytida Maven va Node xotira talab qiladi). Disk: 20 GB.
- Docker Engine va Docker Compose plugin.
- Ochiq portlar: faqat SSH (22). Bot long polling ishlatadi, shuning uchun kiruvchi port kerak emas.

## 2. Birinchi ishga tushirish
```bash
git clone https://github.com/xudoyberdiyev-dev/sellerflow.git && cd sellerflow
cp .env.example .env && nano .env
docker compose up -d --build
docker compose logs -f app    # "Telegram long polling started" va "First SUPERADMIN created"
```

## 3. Yangilash
```bash
git pull
docker compose up -d --build app
```
Flyway migratsiyalari ilova ishga tushganda avtomatik qo'llanadi. Yangilashdan oldin qo'lda backup oling:
`docker compose exec backup pg_dump -Fc -f /backups/manual_$(date +%F).dump`.

## 4. Superadmin panelga kirish
**A) SSH tunnel (tavsiya etiladi, domen kerak emas):**
```bash
ssh -L 8080:127.0.0.1:8080 user@SERVER_IP
# brauzer: http://localhost:8080/admin/
```

**B) Domen + HTTPS (Caddy misoli) — faqat IP allowlist bilan.** Panelni internetga ochganda **IP cheklovi majburiy**
(parol + 2FA + rate-limit yagona himoya bo'lib qolmasin). `docker-compose.yml` ga qo'shimcha servis qo'shing:
```yaml
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes: ["./Caddyfile:/etc/caddy/Caddyfile:ro", "caddy_data:/data"]
```
`Caddyfile` (`203.0.113.10` va `198.51.100.0/24` o'rniga o'zingizning ofis/uy IP manzillaringizni yozing):
```
admin.sizning-domen.uz {
    @allowed remote_ip 203.0.113.10 198.51.100.0/24
    handle @allowed {
        reverse_proxy app:8080
    }
    handle {
        respond "Forbidden" 403
    }
}
```
`volumes:` bo'limiga `caddy_data:` ni qo'shing. Ilova `X-Forwarded-For` ni faqat ichki (private) tarmoqdagi proxy'dan qabul qiladi
(`server.forward-headers-strategy: native`), shuning uchun login rate-limit va audit haqiqiy mijoz IP'sini ko'radi.
IP manzilingiz doimiy bo'lmasa — A) SSH tunnel yoki VPN (WireGuard/Tailscale) dan foydalaning, panelni ochiq qoldirmang.

**C) Favqulodda tiklash (break-glass).** Yagona SUPERADMIN bloklansa, 2FA qurilmasi yo'qolsa yoki parol unutilsa:
1. `.env` ga yozing: `ADMIN_RECOVERY_EMAIL=<admin email>` va `ADMIN_RECOVERY_PASSWORD=<yangi, kamida 12 belgi>`.
2. `docker compose up -d app` — startupda shu admin: faol SUPERADMIN, parol yangilanadi, 2FA va blok tiklanadi,
   barcha sessiyalar bekor qilinadi, auditga `ADMIN_RECOVERED` yoziladi. Email bo'yicha admin topilmasa — yangi SUPERADMIN yaratiladi.
3. Panelga kiring: 2FA qayta ulanadi, so'ng parolni almashtirish majburiy.
4. **Darhol** `.env` dan ikkala qatorni o'chiring va `docker compose up -d app`. (Bir xil recovery paroli ikkinchi marta
   qo'llanmaydi — logda ogohlantirish chiqadi — lekin sirni `.env` da qoldirmang.)

## 5. Backup va tiklash
- `backup` servisi har 24 soatda `./backups/sellerflow_*.dump` (huquq 600) faylini yaratadi. Eski nusxalar (`BACKUP_KEEP_DAYS`, 14 kun) **faqat muvaffaqiyatli backupdan keyin** o'chiriladi va eng yangi `BACKUP_MIN_KEEP` (7) nusxa hech qachon o'chirilmaydi — backup ketma-ket xato bersa ham eski nusxalar qoladi.
- Holat fayli: `./backups/status.json` (`lastSuccessAt`, `lastError`, `consecutiveFailures`). Qo'lda tekshirish: `cat backups/status.json`.
- **Shifrlash (tavsiya):** `.env` da `BACKUP_ENCRYPTION_PASSPHRASE` bering → fayllar `sellerflow_*.dump.gpg` (gpg, AES256). Parolni parol menejerida ham saqlang: usiz backup tiklanmaydi. Qo'lda ochish: `gpg -d backups/<fayl>.dump.gpg > /tmp/x.dump`.
- **Backuplarni server tashqarisiga nusxalang** (server yo'qolsa backup ham yo'qoladi). Off-site nusxa faqat **shifrlangan** fayllar bilan. `rclone` misoli (S3/Backblaze B2/Google Drive va h.k.):
  ```bash
  # bir marta: rclone config  (masalan, "offsite" nomli remote)
  # crontab -e  — har kuni 04:30 da (backup 03:00 atrofida bo'lsa):
  30 4 * * * cd /opt/sellerflow && rclone copy backups offsite:sellerflow-backups --include "sellerflow_*.dump.gpg" --max-age 48h >> /var/log/sellerflow-offsite.log 2>&1
  ```
  Off-site tomonda ham saqlash muddati (lifecycle) sozlang, masalan 30–90 kun.
- Tiklash: `./scripts/restore.sh backups/<fayl>.dump`
- Oylik tiklash sinovi: `./scripts/restore-drill.sh` (ishlab turgan bazaga tegmaydi).

## 6. Monitoring
- Admin panel → «Boshqaruv paneli» → «Tizim holati»: baza, bot holati, oxirgi muvaffaqiyatli so'rov va oxirgi xato.
- `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`. Tashqi uptime monitoring (masalan, UptimeRobot) uchun 4-banddagi HTTPS orqali `/actuator/health` ni ochiq qoldirish mumkin.
- Loglar: `docker compose logs --since 1h app`.
- Backup holati: admin panel «Tizim holati» → Backup; `docker compose ps` (backup servisi `unhealthy` bo'lsa — oxirgi muvaffaqiyatli backup 26 soatdan eski); `/actuator/health` backup xato/eskirgan bo'lsa `DOWN`; `docker compose logs backup | tail`.
- Alert: `.env` da `ALERT_TELEGRAM_CHAT_ID` berilsa, backup xato bersa yoki eskirsa bot shu chat'ga yozadi (bir muammo uchun 24 soatda bir marta).

## 7. Xavfsizlik eslatmalari
- `.env` faylini hech kimga yubormang. Token oshkor bo'lsa, @BotFather → `/revoke` qiling.
- Admin chiqib ketsa yoki telefonini yo'qotsa: panel → Administratorlar → «O'chirish» yoki «2FA reset».
- Bitta bot tokeni bilan faqat **bitta** ilova nusxasi ishlashi kerak (long polling).
