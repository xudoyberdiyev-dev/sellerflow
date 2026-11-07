# SellerFlow — V1.0 Closed Beta

Sotuvchilar uchun Telegram bot. U bir dona mahsulotdan **haqiqatda qancha foyda qolishini** va maqsadli foyda yoki marja uchun **nechaga sotish kerakligini** hisoblaydi.

> Holat: **V1.0 Closed Beta** — faqat Free tarif, to‘lov yo‘q, marketplace API integratsiyasi yo‘q (qiymatlar qo‘lda kiritiladi).
> Asos: `SellerFlow_MASTER` v1.1 (PRD-01, PRD-02.01–02.10) va 05.11.2026 dagi qarorlar — [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Nimalar tayyor

| Bo‘lim | Imkoniyatlar |
|---|---|
| `/start` va onboarding | Welcome → xizmat shartlari (aniq rozilik, versiyasi saqlanadi) → 7 kanaldan biri → ixtiyoriy biznes nomi va ism → tekshirish → faollashtirish → Free. Takroriy `/start` ikkinchi biznes yaratmaydi. Telefon so‘ralmaydi. |
| 💰 Foydani hisoblash | Wizard: tannarx → narx → komissiya (foiz yoki summa, 0, noma’lum) → logistika → qo‘shimcha xarajatlar → tekshirish → natija. Xarajat holatlari: KNOWN / ZERO / UNKNOWN / NOT_SET. Natija PROFIT / BREAK_EVEN / LOSS, aniqligi COMPLETE yoki PARTIAL. Tarixga **faqat «💾 Saqlash»** bilan yoziladi. |
| 🏷 Narxni aniqlash | 3 usul: maqsadli foyda, maqsadli marja, zararsizlik. Narx 1 / 100 / 1 000 so‘m qadamda yuqoriga yaxlitlanadi (standart 1 000) va natija qayta tekshiriladi. «Yechim yo‘q» va «yagona narx yo‘q» holatlari alohida ko‘rsatiladi. |
| 📦 Mahsulotlarim | Ro‘yxat (10 ta/sahifa), qidiruv (nom/SKU), yaratish (nom + tannarx majburiy), maydonlarni alohida tahrirlash (optimistic lock), arxivlash va tiklash, mahsulotdan foyda/narx kalkulyatoriga o‘tish. SKU biznes ichida noyob. |
| 📊 Hisoblashlar tarixi | Foyda va narx natijalari bitta ro‘yxatda. Filtrlar: tur, Bugun / 7 kun / 30 kun / sana oralig‘i (Asia/Tashkent), hisoblash paytidagi nom bo‘yicha qidiruv. O‘zgarmas snapshot, arxivlash/tiklash, «🔁 Qayta hisoblash» (eski yoki hozirgi tannarx bilan yangi qoralama). |
| ⚙️ Sozlamalar | Biznes nomi va ism (tahrirlash/tozalash), asosiy kanalni tasdiq bilan almashtirish, shartlar versiyasi, ikki bosqichli akkaunt o‘chirish so‘rovi (30 kunlik kutish davri). |
| ⭐ Tariflar | Joriy tarif va imkoniyatlar faqat server konfiguratsiyasidan olinadi. Soxta narx ham, «sotib olish» tugmasi ham yo‘q. |
| ❓ `/help` | 12 ta FAQ, Wizard qadamiga mos kontekstli savol, jarayonga xavfsiz qaytish. Support kontakti faqat admin sozlagan bo‘lsa ko‘rinadi. |
| Xavfsizlik | Tenant izolyatsiyasi (begona ID → «topilmadi»), eski tugmalardan himoya (revision + qadam), takroriy `update_id` va Save idempotent, faqat private chat, qoralama oxirgi kiritishdan 24 soat saqlanadi, audit jurnali (tijoriy qiymatlarsiz). |
| 🛡 Web Superadmin (`/admin/`) | Kirish: parol va majburiy 2FA (Google Authenticator). Rollar: SUPERADMIN va SUPPORT. Bo‘limlar: boshqaruv paneli (faqat agregat sonlar), tizim holati, sotuvchilar (faqat metadata), bloklash va tiklash, kirish so‘rovlari, support kontakti, audit jurnali, administratorlar. |
| 🔐 Rozilik asosidagi kirish | Admin so‘rov yuboradi → sotuvchi botda aniq tasdiqlaydi → admin 30–60 daqiqa davomida faqat ko‘ra oladi. Ruxsat boshqa adminga o‘tkazilmaydi, har bir ko‘rish auditga yoziladi. Sotuvchi yoki admin ruxsatni istalgan payt bekor qila oladi, muddati tugagach avtomatik yopiladi. |
| Akkaunt siyosati | Shartlar versiyasi o‘zgarsa, qayta rozilik so‘raladi. O‘chirish so‘rovidan 30 kun o‘tgach akkaunt avtomatik yopiladi (CLOSED). Tijoriy ma’lumotni o‘chirish `DELETION_PURGE_DATA` bayrog‘i bilan boshqariladi va yurist qaroriga qadar o‘chiq turadi. |
| Ishonchlilik | Kunlik avtomatik backup (`./backups`, 14 kun saqlanadi), tiklash va tiklash sinovi skriptlari, spam himoyasi (10 soniyada 20 ta so‘rov), GitHub CI. |

## Ishga tushirish (server, Docker)

Talablar: Docker va Docker Compose o‘rnatilgan Linux server (VPS). Kamida 1 GB RAM.

```bash
git clone https://github.com/xudoyberdiyev-dev/sellerflow.git
cd sellerflow
cp .env.example .env
nano .env        # TELEGRAM_BOT_TOKEN, DB_PASSWORD, ADMIN_API_KEY ni to'ldiring
docker compose up -d --build
docker compose logs -f app     # "Telegram long polling started" chiqishi kerak
```

So‘ng Telegramda botingizga `/start` yozing.

- **Token** faqat `.env` faylida turadi. `.env` gitga tushmaydi. Tokenni chatga, kodga yoki screenshotga qo‘ymang.
- `.env.example` — faqat shablon: sirli kalitlar **bo‘sh** qoladi. CI buni `scripts/check-env-example.sh` va `gitleaks` bilan tekshiradi. Lokal himoya uchun: `pip install pre-commit && pre-commit install` (commitdan oldin secret-scan).
- Bot **long polling** rejimida ishlaydi: domen yoki HTTPS shart emas.
- Bitta token bilan bir vaqtda faqat **bitta** nusxa ishlashi kerak.
- Holatni tekshirish: `curl localhost:8080/actuator/health`.

### Superadmin panel

1. `.env` faylida `ADMIN_BOOTSTRAP_EMAIL` va `ADMIN_BOOTSTRAP_PASSWORD` ni to‘ldiring (parol kamida 12 belgi). Birinchi SUPERADMIN shu ma’lumotlar bilan yaratiladi.
2. Panel faqat serverning o‘zida ochiq (`127.0.0.1:8080`). Kompyuteringizdan kirish uchun SSH tunnel oching:
   ```bash
   ssh -L 8080:127.0.0.1:8080 user@SERVER_IP
   ```
   So‘ng brauzerda oching: **http://localhost:8080/admin/**
3. Birinchi kirishda QR kod chiqadi. Uni Google Authenticator yoki Authy ilovasida skanerlang.
4. Boshqa adminlarni panelning «Administratorlar» bo‘limidan qo‘shasiz.

Doimiy domen va HTTPS orqali kirish kerak bo‘lsa, [`docs/OPERATIONS.md`](docs/OPERATIONS.md) ga qarang.

### Backup

`backup` servisi har 24 soatda bazani `./backups/` papkasiga saqlaydi. Tiklash va oylik tiklash sinovi:

```bash
./scripts/restore.sh backups/sellerflow_YYYYMMDDTHHMMSSZ.dump   # tiklash (joriy bazani almashtiradi!)
./scripts/restore-drill.sh                                        # sinov: ishlab turgan bazaga tegmaydi
```

Backuplarni boshqa joyga ham nusxalab turing, masalan boshqa server yoki bulutga — [`docs/OPERATIONS.md`](docs/OPERATIONS.md).

## Lokal ishlab chiqish

Talablar: Java 21, Maven 3.9+, PostgreSQL 16.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/sellerflow DB_USER=... DB_PASSWORD=...
export TELEGRAM_BOT_TOKEN=...      # test bot tokeni
mvn spring-boot:run
```

`TELEGRAM_BOT_TOKEN` berilmasa, ilova ishga tushadi, lekin bot o‘chiq bo‘ladi (admin API va health ishlaydi).

### Superadmin UI (lokal)

```bash
cd admin-ui && npm install && npm run dev     # http://localhost:5173/admin/ (API -> :8080 proxy)
```

### Testlar

```bash
mvn test
```

- Testlar **haqiqiy PostgreSQL** bilan ishlaydi. Docker kerak emas: embedded PostgreSQL avtomatik ishga tushadi.
- Root foydalanuvchi ostida (CI) embedded PostgreSQL ishlamaydi. U holda tayyor bazani bering:
  `SELLERFLOW_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/sellerflow_test mvn test`.
- Qamrov:
  - Calculation Engine regression matrix (Master PRD 20.2 va Addendum misollari);
  - bot E2E ssenariylari: onboarding, foyda, narx, katalog, tarix, sozlamalar, eski tugmalar, TTL, tenant izolyatsiyasi, guruh chat;
  - admin API: 2FA, rollar, bloklash, rozilik asosidagi kirish;
  - qayta rozilik, o‘chirish davri, spam himoyasi;
  - Telegram JSON formati va TOTP (RFC 6238 test vektori).
- GitHub'ga har push qilinganda testlar avtomatik ishga tushadi (`.github/workflows/ci.yml`).

## Tuzilma

```
src/main/java/uz/sellerflow/
  calculation/   Yagona moliyaviy engine (BigDecimal): profit + price, Money (parse/format)
  account/       users, businesses, consents; onboarding faollashtirish, profil, holatlar
  catalog/       mahsulotlar (tenant-scoped, idempotent create, optimistic lock, arxiv)
  history/       o'zgarmas hisoblash snapshotlari, filtr/qidiruv/sahifalash
  plan/          tarif katalogi va entitlement (DB'dan)
  support/       support kontakti (admin sozlaydi)
  audit/         audit jurnali
  telegram/      Bot API klienti, long polling
  bot/           dispatcher, wizardlar va bo'limlar, matnlar (Texts.java)
  admin/         Superadmin API: auth (parol + TOTP), rollar, sotuvchilar, statistika
  access/        rozilik asosidagi vaqtinchalik READ-ONLY kirish
admin-ui/        Web Superadmin (React + TypeScript + Vite + Tailwind)
scripts/         backup / restore / restore drill
src/main/resources/db/migration/   Flyway sxemasi
```

Bot matnlari bitta joyda: `bot/Texts.java` (uz-Latn). Xizmat shartlari qoralamasi: `resources/bot/terms_uz.txt`.

## Ishga tushirishdan oldin ochiq qolgan ishlar

To‘liq ro‘yxat: [`docs/DECISIONS.md`](docs/DECISIONS.md) → «Ochiq qolganlar». Eng muhimlari:

- **Yuridik tekshiruv:** xizmat shartlari, maxfiylik siyosati, O‘RQ-547 / O‘RQ-1125, retention siyosati.
- **Jonli sinov:** haqiqiy Telegram bot bilan, serverda.
- **Billing (V1.1):** pullik tariflar — PRD-06.
