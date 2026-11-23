# SellerFlow — Production xavfsizligi

**Sana:** 2026-11-23 · Audit natijalari: `docs/PRODUCTION_READINESS_AUDIT.md` · Reja: `docs/PRODUCTION_READINESS_PLAN.md`

## 1. Himoya choralari (holat bilan)

| Soha | Chora | Holat |
|---|---|---|
| Tenant isolation | Har sotuvchi so'rovi `business_id` bilan (JPA va native SQL); callback ID'lari egasi tekshiriladi | PASS — `TenantIsolationIT`, `BotFlowsIT` |
| Admin rollari | SUPERADMIN / SUPPORT; SUPPORT: sotuvchi ma'lumoti, CSV, adminlar, audit — yopiq | PASS — `AdminApiIT` |
| TOTP 2FA | Majburiy; qadam qayta ishlatilmaydi (replay); 5 xato → 15 daqiqa blok | PASS |
| TOTP sirlari | **AES-256-GCM** (`enc:v1:`, V14). Eski ochiq yozuvlar startda shifrlanadi | PASS (2026-11-23 tuzatildi) |
| Step-up | Admin yaratish, 2FA reset, to'liq o'chirish — joriy parol bilan | PASS |
| Roziliksiz kirish | Admin sotuvchi ma'lumotini faqat sotuvchi tasdiqlagan, muddatli (30/45/60 daqiqa), faqat o'qish uchun ruxsat bilan ko'radi; har ko'rish auditlanadi | PASS — `AdminApiIT` |
| Audit log | Admin amallari, kirishlar, eksport, Uzum ulanishi/uzilishi; 365 kun | PASS |
| SQL injection | Barcha qiymatlar parametr (`?`); dinamik WHERE faqat doimiy bo'laklardan | PASS (kod ko'rigi) |
| XSS | Bot: HTML parse mode, foydalanuvchi va Uzum matni `Ctx.esc`; admin: React + CSP + `X-Frame-Options: DENY` | PASS |
| CSRF | Admin API `Authorization: Bearer` (cookie yo'q) — CSRF qo'llanmaydi; token `sessionStorage` da | PASS |
| Rate limiting | Bot: 20 update / 10 s; admin login limiter; request firewall (64 KiB) | PASS |
| Telegram token | Faqat muhitda yoki sir faylida; loglarga URL yozilmaydi (faqat HTTP status / sinf nomi) | PASS |
| Uzum API kalitlari | Botga yuborilgan xabar darhol o'chiriladi; sessiyaga yozilmaydi; AES-256-GCM + AAD (biznes ID); ekranda oxirgi 4 belgi; uzish yoki akkaunt yopilsa o'chiriladi | PASS (mock) |
| Sirlarni boshqarish | `.env` (chmod 600) yoki Docker secrets (`docker-compose.secrets.yml`, `*_FILE`, `/run/secrets`) | PASS (ixtiyoriy secrets rejimi) |
| Docker | App porti faqat `127.0.0.1`; DB tashqariga ochilmagan; xotira limitlari; log rotatsiyasi | PASS (config) |
| HTTPS | Ixtiyoriy Caddy proxy: faqat `/admin/*`, `/actuator` tashqariga yopiq, HSTS | PASS (config), NOT VERIFIED (domen yo'q) |
| Backup shifrlash | gpg AES256 majburiy (`BACKUP_ALLOW_PLAINTEXT=false`); parol process ro'yxatida ko'rinmaydi | PASS (lokal drill) |
| Bog'liqliklar | osv-scanner CI'da; 2026-11-23 da 23 zaiflik yopildi; 2 tasi asoslangan istisno (§3) | PARTIAL |

## 2. Texnik qarzlar bahosi

### 2.1 TOTP sirlari bazada ochiq — **tuzatildi**
- **Avval:** `admins.totp_secret` ochiq matn edi. Baza yoki backup sizib chiqsa, hujumchi parolni bilgan holda 2FA'ni chetlab o'tishi mumkin edi.
- **Endi:**
  - `TotpSecretConverter` (AES-256-GCM, kalit `SELLERFLOW_SECRETS_KEY`, AAD `admin-totp`);
  - `TotpSecretMigrator` startda eski yozuvlarni shifrlaydi;
  - real HTTP login va 2FA bilan toza bazada tekshirildi.
- **Kalit almashsa yoki yo'qolsa:**
  - login xavfsiz rad etiladi (500 emas);
  - 2FA qayta sozlashga o'tmaydi;
  - bazadagi shifrmatn o'zgarmaydi.
- **Tiklash:** (1) to'g'ri kalitni qaytarish yoki (2) `ADMIN_RECOVERY_EMAIL` / `ADMIN_RECOVERY_PASSWORD` bilan break-glass. Bu yo'l 2FA'ni reset qiladi, sessiyalarni bekor qiladi va auditga yozadi.
- **Kalit yo'q bo'lsa:** ilova ishlaydi, lekin logda WARN chiqadi. Production'da kalit **majburiy** (`PRODUCTION_DEPLOYMENT.md`).

### 2.2 Sirlar `docker inspect` da ko'rinadi — **ixtiyoriy yechim qo'shildi**
- **Xavf darajasi:** `docker inspect` faqat `docker` guruhi yoki root uchun ochiq, bu esa amalda root huquqi demak. Root sir faylini ham o'qiy oladi. Shuning uchun real xavf **past-o'rta**: asosan tasodifiy oshkor bo'lish (skrinshot, support'ga `inspect` chiqishini yuborish).
- **Yechim:** `docker-compose.secrets.yml`. `TELEGRAM_BOT_TOKEN` va `SELLERFLOW_SECRETS_KEY` `/run/secrets` dan o'qiladi, ilova ularni o'zi o'qiydi (`SecretFilesEnvironmentPostProcessor`).
- `DB_PASSWORD` hozircha `.env` da qoladi: Postgres faqat ichki Docker tarmog'ida.
- **Qoidalar:**
  - `.env` va `secrets/` — `chmod 600`, egasi `sellerflow`;
  - `docker` guruhiga faqat operator;
  - `docker inspect` chiqishini hech kimga yubormang.

### 2.3 Docker image'lar digest bilan pinlanmagan — **ochiq (LOW)**
Dependabot haftalik yangilaydi. Registry va versiyalangan image'lar — reja M-4.

## 3. Ochiq zaifliklar va asoslangan istisnolar

| ID | Paket | CVSS | Bizga tegishlimi | Qaror |
|---|---|---|---|---|
| GHSA-pc63-qcmh-9cmg (CVE-2026-47884) | spring-webmvc 6.2.19 | 9.8 | **Yo'q.** Faqat `XsltView` + `/**` view rendering bilan ekspluatatsiya qilinadi. SellerFlow'da view rendering yo'q (faqat `@RestController` JSON va statik admin UI) | Istisno 2027-02-28 gacha (`osv-scanner.toml`); Spring Boot 4 ga o'tish — H-1 |
| GHSA-j9f9-w8pj-32f8 (CVE-2026-47890) | spring-webmvc 6.2.19 | 9.8 (Spring: Low 2.6) | **Yo'q.** Server-Sent Events + view fragments ishlatilmaydi | Xuddi shunday |

**Tekshiruv:** `grep -r "XsltView\|SseEmitter\|ServerSentEvent\|RouterFunction\|ViewResolver" src/main/java` bo'sh natija beradi. `pom.xml` da shablon dvigatellari (Thymeleaf, FreeMarker, Mustache) yo'q.

6.2.x uchun tuzatma (6.2.20) faqat Spring'ning pullik Enterprise qo'llab-quvvatlashida mavjud. Ochiq tuzatma — Spring Framework 7.0.9, ya'ni Spring Boot 4.

## 4. Credential rotation (parollarni almashtirish) rejasi

Eski DB va admin parollari ochiq Git tarixida ko'ringan. Ular **faol deb hisoblanadi** va almashtirilishi shart. Bot tokeni avval almashtirilgan; agar shubha bo'lsa, uni ham almashtiring.

| Sir | Qanday almashtiriladi | Kim |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | @BotFather → `/revoke` → yangi token → `.env` yoki `secrets/` → `docker compose up -d app` | Egasi |
| `DB_PASSWORD` | Birinchi deploy'da — yangi tasodifiy parol (`openssl rand -base64 24`). Ishlab turgan bazada: `docker compose exec db psql -U sellerflow -c "ALTER USER sellerflow PASSWORD '…'"` → `.env` → `docker compose up -d` | Egasi |
| Admin paroli | Panel → Profil → parolni almashtirish; yoki `ADMIN_RECOVERY_*` (break-glass, so'ng `.env` dan o'chiring) | Egasi |
| `ADMIN_BOOTSTRAP_PASSWORD` | Faqat birinchi ishga tushirishda ishlatiladi; keyin `.env` dan **o'chiring** | Egasi |
| `SELLERFLOW_SECRETS_KEY` | Almashtirish = barcha shifrlangan sirlar o'qilmaydi (Uzum kalitlari va TOTP). Faqat sizib chiqsa: yangi kalit → sotuvchilar Uzum'ni qayta ulaydi, adminlar `ADMIN_RECOVERY_*` bilan 2FA'ni qayta sozlaydi | Egasi |
| `BACKUP_ENCRYPTION_PASSPHRASE` | Yangi parol keyingi backup'lardan amal qiladi; **eski backup'lar eski parol bilan ochiladi** — ikkalasini parol menejerida saqlang | Egasi |
| Sotuvchi Uzum kalitlari | Sotuvchi Uzum kabinetida kalitni o'chiradi → botda «🔑 Yangi kalit» | Sotuvchi |

Qo'shimcha:
- Repo'ni **private** qilish tavsiya etiladi. Git tarixini qayta yozish (force push) faqat egasining qarori bilan; parollar almashtirilgach shart emas.
- `gitleaks` CI'da har push'da ishlaydi (`292cd7b` dan keyingi tarix).

## 5. Incident response (qisqa)

1. **Aniqlash:** alert chat (backup, Uzum), `docker compose ps` (unhealthy), loglar, sotuvchi shikoyati.
2. **Cheklash:**
   - sir sizib chiqsa — 4-bo'lim bo'yicha darhol almashtiring;
   - admin akkaunti buzilsa — boshqa SUPERADMIN uni bloklaydi yoki `ADMIN_RECOVERY_*` ishlatiladi;
   - ommaviy muammo bo'lsa — `docker compose stop app` (bot to'xtaydi, ma'lumot saqlanadi).
3. **Baholash:** `audit_events` (kim, qachon, nima), admin kirish ruxsatlari, `marketplace_connections.last_error`.
4. **Tiklash:** kerak bo'lsa backup'dan tiklash (`PRODUCTION_DEPLOYMENT.md` §7), so'ng ilovani qayta ishga tushirish.
5. **Xabar berish:** ta'sirlangan sotuvchilarga bot orqali xabar; shaxsiy ma'lumot sizgan bo'lsa — yurist bilan qonuniy muddatlarda (O'RQ-547) xabar berish tartibi.
6. **Xulosa:** 5 ish kuni ichida qisqa postmortem (sabab, ta'sir, chora) — `docs/` ga.

## 6. Muntazam tekshiruvlar
- **Har hafta:** `docker compose ps`, backup holati (admin panel → Tizim holati), alert chat.
- **Har oy:** restore drill (`scripts/restore-drill.sh`), Dependabot PR'lari, CI zaiflik skaneri.
- **Har chorak:** adminlar ro'yxati va rollari, faol kirish ruxsatlari, parollarni almashtirish zarurati.
