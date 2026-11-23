# SellerFlow V3 — Production Readiness Audit

**Sana:** 2026-11-23 · **Kod:** `main` (`dbd06f3` va undan keyingi hujjat commiti) · **Auditor:** Claude (arxitektor / DevOps / QA / Security roli)

## Xulosa

**V3 hozircha production-ready EMAS.** Kod tomoni mustahkam:
- 203 avtomatik test o'tadi;
- migratsiyalar toza bazada ishlaydi;
- shifrlangan backup tiklanishi amalda tekshirildi;
- tenant izolyatsiyasi testlar bilan tasdiqlandi.

Ammo qabul mezonlarining bir qismini faqat real muhitda tekshirish mumkin: real Telegram bot, real Uzum kaliti, server, monitoring va beta sotuvchilar. Ular quyida **NOT VERIFIED** deb belgilangan.

Bundan tashqari audit davomida ikkita jiddiy holat topildi:
- GitHub CI V1+ dan beri **qizil** bo'lgan (sabablari 4-bo'limda).
- Spring Web MVC'da 2 ta Critical zaiflik qoldi. Uning tuzatmasi faqat Spring Boot 4 da bor (FAIL, rejada HIGH).

### Belgilar
- **PASS** — bajarilgan va dalil bilan tasdiqlangan.
- **PARTIAL** — qisman bajarilgan.
- **FAIL** — bajarilmagan.
- **NOT VERIFIED** — real muhit yoki ma'lumot yo'q.

### Audit muhiti (dalillar qayerdan)
- **Lokal:** PostgreSQL 16.x, Java 21, Maven, Node 22. Docker daemon bor, lekin Docker Hub bu muhitdan **yopiq**, shuning uchun image yig'ib, compose'ni konteynerlarda ishga tushirib bo'lmadi.
- **Ilovaning o'zi:** `java -jar` bilan toza bazaga ishga tushirildi.
- **CI:** GitHub Actions natijalari `gh run` orqali o'qildi.
- **Haqiqiy Telegram tokeni, Uzum kaliti, server va domen — yo'q.**

---

## 1. Qabul mezonlari (missiya §10)

| Mezon | Holat | Dalil / izoh |
|---|---|---|
| Barcha kritik testlar PASS | **PASS** | `mvn verify`: 203 test, 0 xato — lokal va CI (`d324a90`, barcha job'lar yashil) |
| Migratsiyalar toza muhitda ishlaydi | **PASS** | Bo'sh `sf_clean` bazasi: Flyway V1–V14 «Successfully applied 14 migrations», ilova `UP` |
| Tenant isolation tasdiqlangan | **PASS** | `TenantIsolationIT` (hisobot, import, tannarx oqimi, Uzum kaliti AAD), avvalgi `BotFlowsIT`/`AdminApiIT` |
| Moliyaviy formulalar testlangan | **PASS** (mock) | `CalculationEngineTest`, `MarketplaceFlowsIT.periodReport…` (qo'lda hisoblangan qiymatlar), chekka qiymatlar. Uzum manbasi bilan solishtirish — **NOT VERIFIED** |
| Telegram bot real muhitda tekshirilgan | **NOT VERIFIED** | Bot oqimlari faqat soxta Telegram (`FakeTelegram`) bilan sinalgan |
| Uzum API real kalit bilan tekshirilgan | **NOT VERIFIED** | Faqat `FakeUzum` va lokal HTTP stub; maydonlar ochiq qo'llanma va swagger asosida |
| Backup va restore amalda sinovdan o'tgan | **PARTIAL** | Lokal: haqiqiy sxema → shifrlangan backup → boshqa bazaga tiklash → jadvallar soni mos, ilova tiklangan bazada `UP`. Production serverda va `postgres:16` image ichida — CI (§4), server — **NOT VERIFIED** |
| Monitoring va alert ishlaydi | **NOT VERIFIED** | Kod bor (backup monitor, healthcheck, Uzum alert). Real `ALERT_TELEGRAM_CHAT_ID` ga xabar yetishi sinalmagan |
| Deploy va rollback yo'riqnomasi tayyor | **PASS** | `docs/PRODUCTION_DEPLOYMENT.md` (bu audit bilan yozildi) |
| 3–5 beta sotuvchi bilan sinovga tayyor | **PARTIAL** | Reja tayyor (`docs/BETA_TEST_PLAN.md`); real server va sotuvchilar yo'q |

---

## 2. Bo'limlar bo'yicha audit

### 2.1 Backend arxitekturasi — **PASS**
- Java 21, Spring Boot 3.5.16, bitta deploy qilinadigan servis. Modullar: `account`, `admin`, `audit`, `bot`, `calculation`, `catalog`, `channel`, `export`, `history`, `marketplace`, `ops`, `plan`, `telegram`.
- Pul — faqat `BigDecimal` (`Money`, 2 kasr, HALF_UP). Hisob formulalari bitta joyda (`CalculationEngine`, versiya 1.1.0; davr hisoboti — `PeriodReportService`).
- Har bir sotuvchi so'rovi `business_id` bilan filtrlanadi (native SQL ham).

### 2.2 PostgreSQL va Flyway — **PASS**
- 14 migratsiya. Toza bazada qo'llanadi (yuqorida). Tiklangan bazada «Schema is up to date».
- Jadvallarda CHECK cheklovlari, FK (`ON DELETE CASCADE` — biznesga bog'liq V11–V13 jadvallar), unique indekslar (idempotentlik), davr indekslari bor.
- Eslatma: migratsiyalarni «pastga» qaytarish (down) yo'q. Rollback = backup'dan tiklash (`PRODUCTION_DEPLOYMENT.md`).

### 2.3 Telegram bot oqimlari — **PARTIAL** (kod va mock PASS, real — NOT VERIFIED)

| Talab (missiya §3.A) | Holat | Dalil |
|---|---|---|
| /start va ro'yxatdan o'tish | PASS (mock) | `BotFlowsIT` |
| Barcha menyular va tugmalar | PASS (mock) | `BotFlowsIT`, `MarketplaceFlowsIT` |
| Eski callback / takroriy so'rov | PASS | revision + qadam tekshiruvi, `processed_updates`, `result_id UNIQUE`, nonce |
| Foydalanuvchilar izolyatsiyasi | PASS | `TenantIsolationIT` (begona ID bilan callback → «eskirgan») |
| Bir vaqtda kelgan xabarlar | PASS | foydalanuvchi bo'yicha navbat, `TelegramPollerTest` |
| Telegram API xatolari va retry | PASS | `HttpTelegramGatewayRetryTest` (429 retry_after, 5xx backoff, read-timeout'da qayta yubormaslik) |
| Long polling va qayta ishga tushish | PASS (mock) / NOT VERIFIED (real) | offset = eng kichik tugamagan update, advisory lock, dead-letter |
| Noto'g'ri yoki bo'sh ma'lumot | PASS | raqam/foiz parser testlari, fayl importi xatolari, chegirma ≥ narx |
| Bot tokeni loglarga tushmasligi | PASS | xatolar faqat HTTP status yoki sinf nomi bilan yoziladi (`HttpTelegramGateway.post`) |

### 2.4 Admin panel va autentifikatsiya — **PARTIAL**
- **PASS:**
  - Haqiqiy HTTP orqali toza bazada login → 2FA sozlash → token. Bu `java -jar` bilan, 2026-11-23 da tekshirildi.
  - Token'siz so'rov → 401.
  - Rollar (SUPERADMIN/SUPPORT), step-up parol, sessiya 8 soat, 5 xatodan keyin 15 daqiqa blok — `AdminApiIT`.
- **PASS (yangi):** TOTP sirlari bazada shifrlangan (`enc:v1:`), oldin ochiq edi. Kalit almashsa login 500 emas, xavfsiz rad etiladi.
- **CSRF:** autentifikatsiya `Authorization: Bearer` sarlavhasi bilan, cookie yo'q, demak CSRF qo'llanmaydi. **PASS**
- **XSS:** React escape qiladi; token `sessionStorage` da; CSP va `X-Frame-Options: DENY` bor. **PASS**
- **NOT VERIFIED:** mobil va desktop ekranlar, loading/xato holatlari brauzerda qayta ko'rilmadi (bu auditda brauzer ishlatilmadi).

### 2.5 Uzum Seller OpenAPI — **PARTIAL / NOT VERIFIED**

| Talab | Holat | Izoh |
|---|---|---|
| Rasmiy endpointlar | PARTIAL | `/v1/shops`, `/v1/product/shop/{id}`, `/v1/finance/orders`, `/v1/finance/expenses` — swagger va ochiq qo'llanma bo'yicha; real javob bilan solishtirilmagan |
| Autentifikatsiya | PASS (stub) | `Authorization: <kalit>` («Bearer»siz) — `UzumClientTest` |
| Maydonlar va turlar | NOT VERIFIED | tolerant parsing (raqam/satr sanalar, `payload` o'rami); `commission` qator jami yoki dona — tekshirilmagan |
| Pagination va katta hajm | PASS (mock) | sahifa 100 / 1000, do'kon bo'yicha 60 / 40 sahifa chegarasi, 5 000 SKU |
| Rate limit / timeout | PASS (stub) | 550 ms tanaffus (endi qulfdan tashqarida), connect 10 s, read 30 s |
| Retry va backoff | PASS | 5xx / tarmoq — 4 urinish; sekundlik 429 — qayta urinish; kunlik 429 — to'xtash |
| Dublikat bo'lmasligi | PASS | `external_sku_id` unique; sotuv `(do'kon, buyurtma, qator)` PK; takroriy sinxron testi |
| Eskirgan ma'lumot | PARTIAL | oxirgi 35 kun qayta o'qiladi; undan eski status o'zgarishlari yangilanmaydi |
| Kalitni saqlash va o'chirish | PASS | AES-256-GCM + AAD (biznes); xabar o'chiriladi; uzish va akkaunt yopilganda o'chiriladi; begona biznesga ko'chirilgan shifrmatn ochilmaydi |
| Xatolarni log qilish | PASS | `last_error`, `finance_error`, kunlik yig'ma log; tizimli xatoda operatorga alert (yangi) |
| WAF (xorijiy IP) | NOT VERIFIED | 403 HTML aniqlanadi; serverning IP'si bloklanadimi — noma'lum |

### 2.6 Uzum moliya sinxroni — **PARTIAL**
- Mock bilan PASS: statuslar, xarajat kodlari (Kiril/lotin), takroriy o'qish, moliya xatosi mahsulot sinxronini buzmasligi.
- **NOT VERIFIED:**
  - `PARTIALLY_CANCELLED` da `amount` qoldiq sonmi?
  - `commission` va `logisticDeliveryFee` qator jamimi?
  - `date` formati qanday?
  - `INCOME` kompensatsiyalar qanday keladi?

### 2.7 Davr hisoboti va sof foyda — **PASS** (formula) / **NOT VERIFIED** (Uzum bilan solishtirish)

| Ko'rsatkich | Holat | Qayerda tekshirilgan |
|---|---|---|
| Tushum | PASS | `periodReport…` (250 000), katta qiymat (999 999 999 999 × 1000) |
| Chegirma | PASS | `CalculationEngineTest` (foiz/summa, ≥ narx — xato, teskari narx) |
| Komissiya, logistika, Uzum to'lovi | PASS | `periodReport…` (50 000 / 15 000 / 185 000) |
| Qaytarish | PASS (1 dona hisobida) / PARTIAL (davr) | davr hisobotida qaytgan pul alohida qator emas — bekor qilinganlar soni va qaytim saqlash xarajati |
| Reklama, saqlash, jarima | PASS | xarajat tasnifi va bekor qilingan / ichki o'tkazma chiqarib tashlanadi |
| Tannarx | PASS | sotuvchi kiritgani → qatordagi `purchasePrice`; yo'q bo'lsa ogohlantirish |
| Soliq | PASS | Uzum kanal stavkasidan; yo'q bo'lsa ochiq ogohlantirish |
| Sof foyda, marja | PASS | 12 000 / 4,8% (qo'lda hisoblangan) |
| Yo'ldagi pul | PASS | 75 000 (PROCESSING) |
| Izchillik | PASS | bir xil davr ikki marta → bir xil natija (`TenantIsolationIT`) |
| Nol / manfiy / katta | PASS | nol narx, nol soni, 10^15 tushum; manfiy xarajat `abs` |
| Uzum kabineti bilan solishtirish | **NOT VERIFIED** | real kalit kerak |

### 2.8 Xavfsizlik — **PARTIAL** → batafsil: `docs/PRODUCTION_SECURITY.md`

### 2.9 Docker Compose — **PARTIAL**
- **PASS:**
  - `docker compose config` (asosiy, `proxy`, `secrets` override'lar) to'g'ri yig'iladi.
  - Healthcheck'lar: db, app (DB + poller), backup (26 soat).
  - `restart: unless-stopped`, xotira limitlari, log rotatsiyasi (3 × 10 MB).
  - App porti faqat `127.0.0.1`.
- **NOT VERIFIED:** image yig'ish va konteynerlarda ishga tushirish bu muhitda (Docker Hub yopiq). CI'dagi `docker` job buni qiladi.

### 2.10 Backup, restore, monitoring — **PARTIAL**
- **PASS (lokal):**
  - `backup-loop.test.sh` va `restore.test.sh` o'tdi.
  - Haqiqiy sxema bilan drill: shifrlangan `.dump.gpg` → `pg_restore --list` → yangi bazaga tiklash → 8 jadval soni mos → Flyway V14 → TOTP tiklangan bazada ham shifrlangan → ilova tiklangan bazada `UP`.
- **FAIL → tuzatildi:** CI'da restore testi PostgreSQL 17 klienti bilan yiqilardi. Endi skriptlar `postgres:16` ichida ishlaydi va image'da `gpg` borligi ham tekshiriladi.
- **NOT VERIFIED:**
  - server tashqarisiga nusxa (rclone misoli bor, avtomatlashtirilmagan);
  - alert real chatga yetishi;
  - production serverda drill.

### 2.11 GitHub Actions CI/CD — **FAIL → tuzatildi (natija §4)**
- V1+ (`39300f3`) dan `a5be005` gacha barcha run'lar **failure** edi. Sabablari:
  1. `test` job: backup/restore testi (pg_dump 17 → PG 16).
  2. `vulnerability-scan`: 9 paketda 25 ta ma'lum zaiflik.
- Audit qatnashchisi (Claude) bu run'larni oldin tekshirmagan — bu jarayon xatosi. Endi har push'dan keyin CI natijasi tekshiriladi.
- CD (avtomatik deploy) yo'q — bu ataylab. Deploy qo'lda, `PRODUCTION_DEPLOYMENT.md` bo'yicha.

### 2.12 Testlar — **PASS**
- 203 test (unit + integratsiya). Ichida:
  - moliya engine;
  - Telegram oqimlari;
  - admin API (auth, rollar, step-up, rozilik asosida kirish);
  - Uzum (klient stub, mapping, moliya tasnifi, sinxron);
  - tenant izolyatsiyasi;
  - fayl parser (XXE, zip-bomb);
  - sir fayllari, TOTP shifrlash;
  - backup monitor;
  - poller.
- Shell testlari: backup-loop va restore.
- **Yo'q (NOT VERIFIED):**
  - real Uzum bilan contract test (kalit kerak);
  - real Telegram e2e;
  - brauzerdagi UI e2e (bu auditda ishga tushirilmadi).

---

## 3. Eng muhim kamchiliklar (ustuvorlik bilan)

| # | Daraja | Kamchilik | Holat |
|---|---|---|---|
| 1 | CRITICAL | Spring Web MVC 6.2.19 da 2 ta Critical (CVSS 9.8) zaiflik; tuzatma faqat 7.0.9 (Spring Boot 4) | **Ochiq** — Boot 4 ga o'tish rejada (HIGH, 2–5 kun). Vaqtinchalik: admin faqat 127.0.0.1 / proxy, bot kiruvchi port ochmaydi |
| 2 | HIGH | CI V1+ dan beri qizil; zaif kutubxonalar (jackson, tomcat 9.8, postgresql 8.2, log4j-api, commons-io, minimatch) | **Tuzatildi** (versiyalar yangilandi, CI tuzatildi) |
| 3 | HIGH | Real Uzum API bilan tekshirilmagan (maydonlar, WAF) | **Ochiq** — real kalit kerak |
| 4 | HIGH | Real Telegram bot va serverda tekshirilmagan | **Ochiq** — server va token kerak |
| 5 | HIGH | Admin TOTP sirlari bazada ochiq | **Tuzatildi** (AES-256-GCM, V14) |
| 6 | HIGH | Shifrlash kaliti almashsa admin login 500 | **Tuzatildi** (xavfsiz rad, shifrmatn saqlanadi) |
| 7 | HIGH | Eski parollar ochiq Git tarixida | **Ochiq** — rotatsiya rejasi (`PRODUCTION_SECURITY.md` §4), egasi bajaradi |
| 8 | MEDIUM | Backup server tashqarisiga avtomatik ko'chirilmaydi | **Ochiq** — egasi (rclone + bulut) |
| 9 | MEDIUM | Sirlar `docker inspect` da ko'rinadi | **Tuzatildi** (ixtiyoriy `docker-compose.secrets.yml`) |
| 10 | MEDIUM | HTTPS reverse proxy konfiguratsiyasi yo'q edi | **Tuzatildi** (`docker-compose.proxy.yml`, Caddy) |
| 11 | MEDIUM | Uzum sinxron qulfi boshqa sotuvchilarni kutdirardi; tungi xatolar jim edi | **Tuzatildi** |
| 12 | MEDIUM | 2 GB RAM chegarada (db 512M + app 768M + backup 256M + proxy 128M) | **Tavsiya:** beta uchun 4 GB / 2 vCPU |
| 13 | MEDIUM | Yuridik hujjatlar qoralama | **Ochiq** — yurist |

---

## 4. CI natijasi (tuzatishdan keyin)
`dbd06f3` uchun GitHub Actions natijasi pastda yozilgan. Bu muhitda CI kuzatildi va natija shu yerga qo'shildi.

**Run 45145061065** (`dbd06f3`, 2026-11-23):

| Job | Natija | Izoh |
|---|---|---|
| test | ✅ success | `mvn verify`: 203 test, 0 xato; `postgres:16` ichida `gpg (GnuPG) 2.4.7`, `backup-loop.test: OK`, `restore.test: OK` |
| docker | ✅ success | image yig'ildi; asosiy, `proxy`, `secrets` compose konfiguratsiyalari to'g'ri |
| secrets (gitleaks) | ✅ success | |
| shellcheck | ✅ success | `deploy/*.sh` ham |
| dependency-report | ✅ success | |
| vulnerability-scan | ❌ failure | 25 zaiflikdan 23 tasi yopildi; qolgan 2 tasi — spring-webmvc 6.2.19 (CVE-2026-47884, CVE-2026-47890), ochiq tuzatma faqat 7.0.9 da. Bizga tegishli emasligi asoslangan (`PRODUCTION_SECURITY.md` §3), keyingi commitda `osv-scanner.toml` bilan 2027-02-28 gacha istisno + H-1 reja |

**Run `d324a90`** (hujjatlar + `osv-scanner.toml`): **barcha 6 job — success** (test, docker, secrets, shellcheck, vulnerability-scan, dependency-report). CI V1+ dan beri birinchi marta to'liq yashil.
