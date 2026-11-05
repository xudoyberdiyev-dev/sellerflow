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

**B) Domen + HTTPS (Caddy misoli).** `docker-compose.yml` ga qo'shimcha servis qo'shing:
```yaml
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes: ["./Caddyfile:/etc/caddy/Caddyfile:ro", "caddy_data:/data"]
```
`Caddyfile`:
```
admin.sizning-domen.uz {
    reverse_proxy app:8080
}
```
`volumes:` bo'limiga `caddy_data:` ni qo'shing. Panel faqat parol va 2FA bilan himoyalangan. Imkon bo'lsa, Caddy'da IP cheklovini ham qo'ying.

## 5. Backup va tiklash
- `backup` servisi har 24 soatda `./backups/sellerflow_*.dump` faylini yaratadi va 14 kundan eskilarini o'chiradi.
- **Backuplarni server tashqarisiga nusxalang** (server yo'qolsa backup ham yo'qoladi). Masalan, cron orqali `rclone` yoki `scp` bilan.
- Tiklash: `./scripts/restore.sh backups/<fayl>.dump`
- Oylik tiklash sinovi: `./scripts/restore-drill.sh` (ishlab turgan bazaga tegmaydi).

## 6. Monitoring
- Admin panel → «Boshqaruv paneli» → «Tizim holati»: baza, bot holati, oxirgi muvaffaqiyatli so'rov va oxirgi xato.
- `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`. Tashqi uptime monitoring (masalan, UptimeRobot) uchun 4-banddagi HTTPS orqali `/actuator/health` ni ochiq qoldirish mumkin.
- Loglar: `docker compose logs --since 1h app`.
- Backup holati: `docker compose logs backup | tail`.

## 7. Xavfsizlik eslatmalari
- `.env` faylini hech kimga yubormang. Token oshkor bo'lsa, @BotFather → `/revoke` qiling.
- Admin chiqib ketsa yoki telefonini yo'qotsa: panel → Administratorlar → «O'chirish» yoki «2FA reset».
- Bitta bot tokeni bilan faqat **bitta** ilova nusxasi ishlashi kerak (long polling).
