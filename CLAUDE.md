# SellerFlow — Claude Code uchun yo'riqnoma

Javoblarni o'zbek tilida (lotin) yozing.

## Loyiha
- Backend + Telegram bot: Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway, Maven (`src/`).
- Superadmin UI: React + TypeScript + Vite + dizayn tizimi (`admin-ui/src/styles`, `design/superadmin/`) (`admin-ui/`), production'da `/admin/` da Spring orqali beriladi.
- Hujjatlar: `README.md`, `docs/DECISIONS.md` (qarorlar), `docs/OPERATIONS.md` (ekspluatatsiya).

## Ishga tushirish (lokal, Docker bilan)
1. Docker Desktop ishlayotganini tekshiring: `docker info`.
2. `.env` yo'q bo'lsa: `.env.example` dan nusxa oling. `TELEGRAM_BOT_TOKEN`, `DB_PASSWORD`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` (>= 12 belgi) to'ldirilishi shart.
   - **Tokenni foydalanuvchidan chatda so'ramang va hech qayerga chiqarmang** — foydalanuvchi uni `.env` ga o'zi yozadi. `.env` ni o'qib, qiymatlarini ekranga chiqarmang; faqat bo'sh/to'ldirilganini tekshiring.
3. `docker compose up -d --build` (birinchi marta 5–10 daqiqa).
4. Tekshirish: `docker compose logs --tail 100 app` — "Telegram long polling started" va "Started SellerFlowApplication".
5. Bot: Telegram'da `/start`. Admin panel: http://localhost:8080/admin/ (birinchi kirishda 2FA QR).
- To'xtatish: `docker compose down`. Ma'lumot `pgdata` volume'da saqlanadi (`down -v` hammasini o'chiradi — foydalanuvchi so'ramasa ishlatmang).

## Tez-tez uchraydigan muammolar
- `TELEGRAM_BOT_TOKEN .env faylida berilishi shart` → `.env` to'ldirilmagan.
- Logda `Telegram getUpdates failed: HTTP 401` → token noto'g'ri. `HTTP 409` → shu token bilan boshqa nusxa ishlayapti.
- Port 8080 band → `docker-compose.yml` dagi `127.0.0.1:8080:8080` ni masalan `127.0.0.1:8081:8080` ga o'zgartiring.
- Windows'da `backup` servisi `$'\r'` xatosi → `.sh` fayllar CRLF bo'lib qolgan; `git config core.autocrlf false` va qayta clone.

## Ishlab chiqish
- Testlar: `mvn test` (embedded PostgreSQL; root ostida ishlamaydi — u holda `SELLERFLOW_TEST_DB_URL` bering).
- UI: `cd admin-ui && npm install && npm run dev` → http://localhost:5173/admin/ (API `:8080` ga proxy).
- Bot matnlari: `src/main/java/uz/sellerflow/bot/Texts.java`. Hisob formulalari: `calculation/CalculationEngine.java` — o'zgartirsangiz `CalculationEngineTest` ni yangilang.
- Pul uchun faqat `BigDecimal`. Tenant ma'lumoti har doim `business_id` bo'yicha filtrlanadi.
