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
| Admin API | `X-Admin-Key` bilan: statistika, sotuvchilar ro‘yxati (faqat metadata), suspend/reactivate, support kontakti, audit. Web Superadmin — keyingi bosqich (PRD-07). |

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
- Bot **long polling** rejimida ishlaydi: domen yoki HTTPS shart emas.
- Bitta token bilan bir vaqtda faqat **bitta** nusxa ishlashi kerak.
- Holatni tekshirish: `curl localhost:8080/actuator/health`.

### Admin API misollari

```bash
K=$(grep ADMIN_API_KEY .env | cut -d= -f2)
curl -H "X-Admin-Key: $K" localhost:8080/admin/api/v1/stats
curl -H "X-Admin-Key: $K" "localhost:8080/admin/api/v1/sellers?page=0&size=50"
curl -X PUT -H "X-Admin-Key: $K" -H "Content-Type: application/json" \
     -d '{"enabled":true,"label":"Qo‘llab-quvvatlash","url":"@your_support"}' localhost:8080/admin/api/v1/support
curl -X POST -H "X-Admin-Key: $K" -H "X-Admin-Id: azizbek" -H "Content-Type: application/json" \
     -d '{"reason":"spam"}' localhost:8080/admin/api/v1/sellers/<telegram_user_id>/suspend
curl -H "X-Admin-Key: $K" localhost:8080/admin/api/v1/audit
```

Port 8080 faqat `127.0.0.1` da ochiq. Admin API’ni tashqariga chiqarish kerak bo‘lsa, oldiga HTTPS reverse proxy (masalan, Caddy yoki nginx) qo‘ying.

### Backup

```bash
docker compose exec db pg_dump -U sellerflow sellerflow | gzip > backup_$(date +%F).sql.gz
```

Production’dan oldin avtomatik backup va **tiklash sinovi** (restore drill) majburiy (Master PRD 19.7).

## Lokal ishlab chiqish

Talablar: Java 21, Maven 3.9+, PostgreSQL 16.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/sellerflow DB_USER=... DB_PASSWORD=...
export TELEGRAM_BOT_TOKEN=...      # test bot tokeni
mvn spring-boot:run
```

`TELEGRAM_BOT_TOKEN` berilmasa, ilova ishga tushadi, lekin bot o‘chiq bo‘ladi (admin API va health ishlaydi).

### Testlar

```bash
mvn test
```

- Testlar **haqiqiy PostgreSQL** bilan ishlaydi. Docker kerak emas: embedded PostgreSQL avtomatik ishga tushadi.
- Root foydalanuvchi ostida (CI) embedded PostgreSQL ishlamaydi. U holda tayyor bazani bering:
  `SELLERFLOW_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/sellerflow_test mvn test`.
- Qamrov: Calculation Engine regression matrix (Master PRD 20.2 va Addendum misollari), bot E2E ssenariylari (onboarding, foyda, narx, katalog, tarix, sozlamalar, eski tugmalar, TTL, tenant izolyatsiyasi, guruh chat), admin API, Telegram JSON formati.

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
  admin/         minimal admin API
src/main/resources/db/migration/   Flyway sxemasi
```

Bot matnlari bitta joyda: `bot/Texts.java` (uz-Latn). Xizmat shartlari qoralamasi: `resources/bot/terms_uz.txt`.

## Ishga tushirishdan oldin ochiq qolgan ishlar

[`docs/DECISIONS.md`](docs/DECISIONS.md) → «Ochiq qolganlar» bo‘limiga qarang. Eng muhimlari:

- yuridik tekshiruv (O‘RQ-547 / O‘RQ-1125, xizmat shartlari);
- Web Superadmin (PRD-07);
- backup va monitoring;
- 30 kunlik o‘chirish davridan keyingi ma’lumotlarni qayta ishlash.
