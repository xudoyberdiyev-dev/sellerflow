# SellerFlow: Production Readiness Audit hisoboti

| | |
|---|---|
| **Sana** | 2026-11-06 |
| **Obyekt** | `sellerflow` repozitoriysi, `main` @ `f4a1d6e` (+ commit qilinmagan `.env.example` o'zgarishi) |
| **Hajm** | 99 fayl, ~9 700 qator (Java 21 / Spring Boot 3.3.5, PostgreSQL + Flyway, React/Vite admin UI, Docker Compose, CI) |
| **Usul** | Faqat o'qish va skanerlash. Kod o'zgartirilmagan. Build va testlar repo nusxasida (scratchpad) bajarilgan |
| **Tekshiruvchilar** | 9 yo'nalish bo'yicha mustaqil auditorlar → har bir topilma uchun 1–2 ta adversarial (rad etishga urinuvchi) tekshiruvchi → yetishmagan joylarni qidiruvchi tanqidchi. Jami 144 agent |

---

## 1. Yakuniy xulosa (Executive Summary)

### Hukm: ❌ Ommaviy (public) production uchun **HALI TAYYOR EMAS**. ⚠️ Yopiq beta uchun **SHARTLI TAYYOR**

Loyiha beta bosqichi uchun **yuqori sifatda** yozilgan. Pul hisob-kitoblari `BigDecimal` bilan to'g'ri ishlaydi. Tenant izolyatsiyasi izchil. Idempotentlik, optimistic lock, BCrypt, majburiy 2FA va rozilik asosidagi kirish bor. Testlar 79/79 o'tadi, satr qamrovi 77.9%. Lekin productionga chiqishdan oldin **bitta kritik** va **uchta yuqori** darajadagi muammo albatta tuzatilishi kerak.

| Daraja | Soni | Ma'nosi |
|---|---|---|
| 🔴 CRITICAL | **1** | Darhol: maxfiy ma'lumotlar sizib chiqish xavfi |
| 🟠 HIGH | **3** | Public launchgacha majburiy |
| 🟡 MEDIUM | **31** | Launchdan keyin tez orada (1–4 hafta) |
| 🟢 LOW | **~84** | Texnik qarz, sifatni oshirish |
| ✅ Rad etilgan | 5 | Tekshiruvda tasdiqlanmagan da'volar |

**Umumiy baho: 6.5 / 10** (yopiq beta uchun 8 / 10)

| Yo'nalish | Baho | Izoh |
|---|---|---|
| Biznes mantiq (hisob-kitob) | 9/10 | BigDecimal, 200 000 holatli fuzz test o'tdi, faqat chekka holatlarda kamchilik bor |
| Bot xavfsizligi / tenant izolyatsiyasi | 8/10 | Izchil `businessId` filtrlash, stale-button himoyasi |
| Ma'lumotlar bazasi / tranzaksiyalar | 7.5/10 | Constraint va indekslar to'g'ri. Bir nechta lost-update xavfi bor |
| Admin panel xavfsizligi | 5/10 | **Auth bypass**, TOTP replay, lockout DoS |
| Ishonchlilik / Telegram | 6/10 | Bitta oqim, 429 ishlanmaydi, health botni aks ettirmaydi |
| DevOps / Backup | 5/10 | Backup xatosi jim o'tadi, off-site nusxa yo'q, Spring Boot EOL |
| Maxfiylik / huquqiy | 5/10 | Shartlar matni qoralama, privacy policy yo'q, "o'chirish" ma'lumotni o'chirmaydi |
| Testlar / CI | 7/10 | 79 test, 77.9% qamrov. Poller va admin boshqaruvi testlanmagan |
| Admin UI | 8/10 | Build va lint toza, XSS yo'q, CSP mos |

---

## 2. 🔴 CRITICAL

### C-1. Haqiqiy production sirlari git kuzatadigan `.env.example` fayliga yozilgan
- **Fayl:** `.env.example:5, 9, 13-14` (hozircha **commit qilinmagan**, `git status` → `M .env.example`)
- **Dalil:** Bo'sh placeholderlar haqiqiy qiymatlarga almashtirilgan: `TELEGRAM_BOT_TOKEN`, `DB_PASSWORD`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` (qiymatlar va ularning prefikslari 2026-11-13 da hisobotdan olib tashlandi). Fayl 23:06 da o'zgargan. Audit agentlari bu faylga yozmagan, faqat o'qigan.
- **Ta'sir:** `.env.example` `.gitignore`da emas. Keyingi `git add .` / `git commit -a` bilan token va superadmin paroli GitHubga ketadi. Bot tokeni bilan har kim botni boshqarib, foydalanuvchilarga xabar yubora oladi. Superadmin paroli bilan admin panelga kirish xavfi ham bor (2FA hali himoya qiladi). Bundan tashqari, `DB_PASSWORD` qiymati zaif.
- **Tavsiya:**
  1. Bu qiymatlarni `.env` fayliga ko'chiring. `.env.example`ni `git checkout -- .env.example` bilan asl holatiga qaytaring.
  2. Fayl biror joyga yuborilgan, ulashilgan yoki commit qilingan bo'lsa, **@BotFather → /revoke** orqali tokenni yangilang va parollarni almashtiring.
  3. `gitleaks` yoki `pre-commit` secret-scan hook qo'shing.
  4. DB paroli uchun `openssl rand -hex 24` ishlating.

---

## 3. 🟠 HIGH (public launchgacha majburiy)

### H-1. Admin API autentifikatsiyasini URL orqali chetlab o'tish mumkin (auth bypass)
- **Fayl:** `src/main/java/uz/sellerflow/admin/AdminController.java:383-388` (`AdminAuthFilter.shouldNotFilter`)
- **Dalil:** Filtr xom `request.getRequestURI()` ni `startsWith("/admin/api/")` bilan tekshiradi. Spring MVC esa dekodlangan va `;`-parametrlardan tozalangan yo'lni moslaydi. Auditor shu versiyalarda (Spring Boot 3.3.5 / Tomcat 10.1.31) **amalda takrorladi**:
  - `GET /admin/api/v1/sellers` → 401
  - `GET /admin/api;x=1/v1/sellers` → **200 (ma'lumot qaytdi)**
  - `GET /admin;/api/v1/sellers` → **200**
  - `GET /admin/%61pi/v1/sellers` → **200**
- **Ta'sir:** Login va 2FA'siz `/sellers` (barcha foydalanuvchilarning Telegram ID, status, kanal, tarif, sanalar), `/stats`, `/health` (bot xatosi matni bilan) va `GET /support` ochiq qoladi. Yozish endpointlari `@RequestAttribute Principal` talab qilgani uchun 400 qaytaradi. Standart sozlamada port faqat `127.0.0.1` ga bog'langan, lekin `docs/OPERATIONS.md` §4B (Caddy + HTTPS) varianti panelni internetga ochadi. Shu sababli tekshiruvchilar darajani CRITICAL → **HIGH** ga tushirdi.
- **Tavsiya:** Spring Security `requestMatchers("/admin/api/**")` ishlating (StrictHttpFirewall `;` va `%` ni rad etadi) yoki `HandlerInterceptor` + `addPathPatterns`. Har bir endpoint Principal talab qilsin (deny-by-default). Shu URLlar uchun regression test yozing.

### H-2. Backup xatolari jim o'tadi: alert, healthcheck va yangilik tekshiruvi yo'q
- **Fayl:** `scripts/backup-loop.sh:8-14`, `docker-compose.yml` (backup servisi)
- **Dalil:** Xato faqat `echo "[backup] FAILED" >&2` bilan loglanadi. Bundan tashqari, **14-qator xato bo'lsa ham eski backuplarni o'chirishda davom etadi** (tanqidchi topdi, qo'lda tasdiqlandi). 14 kundan ortiq ketma-ket xatodan keyin **birorta ham backup qolmaydi**.
- **Ta'sir:** Ma'lumot yo'qolishi xavfi. Operator buni faqat tiklash kerak bo'lganda biladi.
- **Tavsiya:** Xatoda Telegram yoki email alert yuboring. Healthcheck "oxirgi muvaffaqiyatli backup < 26 soat" shartini tekshirsin. Rotatsiyani faqat muvaffaqiyatli backupdan keyin bajaring va kamida N ta oxirgi nusxani har doim saqlang.

### H-3. Foydalanuvchiga ko'rsatiladigan xizmat shartlari tekshirilmagan qoralama, privacy policy yo'q
- **Fayl:** `src/main/resources/bot/terms_uz.txt:1-8`
- **Ta'sir:** Rozilik huquqiy kuchga ega bo'lmasligi mumkin. O'zR "Shaxsga doir ma'lumotlar to'g'risida"gi qonuni (ma'lumot operatori, maqsad, saqlash muddati, lokalizatsiya talabi) bo'yicha xavf bor.
- **Tavsiya:** Yurist tasdiqlagan shartlar va maxfiylik siyosatini qo'shing: operator nomi, kontakt, saqlanadigan ma'lumotlar, muddat, o'chirish tartibi, server joylashuvi.

---

## 4. 🟡 MEDIUM (launchdan keyin tez orada)

### 4.1 Admin xavfsizligi
| # | Muammo | Joy |
|---|---|---|
| M-1 | **TOTP kodini qayta ishlatish (replay):** bitta kod ±1 qadam oynasida (~90 s) cheksiz marta ishlaydi, oxirgi ishlatilgan qadam saqlanmaydi | `admin/Totp.java:31-40`, `AdminAuthService.java:220-224` |
| M-2 | **IP darajasida rate-limit yo'q.** Akkaunt lockouti orqali har kim adminlarni bloklay oladi (DoS). Lockout javobi parol to'g'riligini ham oshkor qiladi | `AdminAuthService.java:205-218, 254-262` |
| M-3 | Muvaffaqiyatsiz login va TOTP hisoblagichlari parallel so'rovlarda o'sishlarni yo'qotadi, shuning uchun lockoutni chetlab o'tish mumkin | `AdminAuthService.java:254-262` |
| M-4 | Parol o'zgarganda boshqa sessiyalar bekor qilinmaydi, eski parolni tekshirishda brute-force himoyasi yo'q | `AdminAuthService.java:283-292` |
| M-5 | Vaqtinchalik paroldan keyin "parolni almashtiring" faqat banner: serverda majburiy emas, sahifa yangilansa yo'qoladi. Testlar ham shu xatti-harakatni mustahkamlaydi | `admin-ui/src/App.tsx:17,45,103-107`, `AdminApiIT.java:114-119` |
| M-6 | Audit jurnalida xavfsizlik hodisalari (muvaffaqiyatsiz login, lockout) va manba IP yo'q | `AdminAuthService.java:201-222`, `AuditService` |
| M-7 | Admin tiklash yo'li yo'q: yagona SUPERADMIN bloklansa, "break-glass" mexanizmi mavjud emas | `AdminAuthService.java:175-191` |
| M-8 | Hujjatlashtirilgan public HTTPS varianti admin login'ni internetga faqat per-account lockout bilan ochadi | `docs/OPERATIONS.md:31-45` |

### 4.2 Ishonchlilik va Telegram integratsiyasi
| # | Muammo | Joy |
|---|---|---|
| M-9 | **Bitta oqim:** barcha updatelar ketma-ket, sinxron Telegram chaqiruvlari bilan (read timeout 45 s) qayta ishlanadi. Bitta sekin chaqiruv yoki bir nechta faol foydalanuvchi botni hamma uchun sekinlashtiradi | `telegram/TelegramPoller.java:48-50, 62-71`, `HttpTelegramGateway.java` |
| M-10 | **DB uzilganda** kelgan barcha updatelar butunlay yo'qoladi, foydalanuvchiga javob berilmaydi | `TelegramPoller.java:62-71`, `BotDispatcher.java:66,111` |
| M-11 | Chiquvchi Telegram xatolari yutib yuboriladi: 429 `retry_after` ishlanmaydi, qayta urinish yo'q, xabar jim yo'qoladi | `HttpTelegramGateway.java:96-106` |
| M-12 | `/actuator/health` bot holatini aks ettirmaydi: poller o'lgan bo'lsa ham UP qaytaradi. Docker `app` servisida healthcheck yo'q | `application.yml:24-31`, `docker-compose.yml:21-41` |
| M-13 | Metrikalar va observability yo'q (Prometheus/Micrometer, alertlar) | `application.yml:24-31` |
| M-14 | Ism emoji o'rtasida qirqilganda Telegramga yolg'iz UTF-16 surrogate yuboriladi, natijada xabar yuborilmaydi | `bot/ProductHandler.java:101`, `HistoryHandler.java:152,171` |

### 4.3 DevOps va backup
| # | Muammo | Joy |
|---|---|---|
| M-15 | Backup DB bilan bir xil disk va hostda turadi. Off-site nusxa faqat hujjatda bor | `docker-compose.yml:44-60` |
| M-16 | Backup fayllari (seller PII bilan) shifrlanmagan, hostdagi har bir foydalanuvchi o'qiy oladi | `scripts/backup-loop.sh:4-8` |
| M-17 | Backup rotatsiyasi xato paytida ham oxirgi yaxshi nusxalarni o'chiradi (H-2 ga qarang) | `scripts/backup-loop.sh:14` |
| M-18 | `restore.sh` atomar emas, oldidan xavfsizlik snapshoti olinmaydi, yangi sxemadagi obyektlar qolib ketadi | `scripts/restore.sh:9-11` |
| M-19 | **Spring Boot 3.3.5 OSS qo'llab-quvvatlash muddati tugagan.** Ichidagi Tomcat/Spring versiyalari yamalmagan | `pom.xml:10` |
| M-20 | Konteynerda xotira limiti yo'q, JVM `MaxRAMPercentage=75`. RAM bo'yicha hujjatlar bir-biriga zid | `Dockerfile:26`, `docker-compose.yml` |

### 4.4 Testlar
| # | Muammo | Joy |
|---|---|---|
| M-21 | Admin boshqaruvi va parol almashtirish endpointlari 0% testlangan (xavfsizlik uchun kritik yo'llar) | `AdminController.java:114,231,274,288,300-310` |
| M-22 | `TelegramPoller` (asosiy production loop) 0% testlangan: offset, backoff va xatolarni ishlash tekshirilmagan | `TelegramPoller.java:39-88` |

### 4.5 Maxfiylik va mahsulot oqimi
| # | Muammo | Joy |
|---|---|---|
| M-23 | **"Akkauntni o'chirish"** tijoriy ma'lumot va Telegram ID'ni muddatsiz saqlaydi. Foydalanuvchiga bu aytilmaydi | `account/DeletionJob.java:38-58` |
| M-24 | CLOSED akkaunt bilan o'sha Telegram foydalanuvchi botdan **hech qachon** qayta foydalana olmaydi | `AccountService.java:52-61`, `BotDispatcher.java:170-172` |
| M-25 | O'chirishni kutayotgan yoki bloklangan foydalanuvchi standart holatda o'chiq support kontaktiga yo'naltiriladi, shuning uchun o'chirishni bekor qila olmaydi | `bot/Texts.java:53-56, 245-248` |
| M-26 | Onboardingni tugatmagan foydalanuvchi ma'lumotini o'chira olmaydi. `users` yozuvi rozilikdan oldin yaratiladi | `AccountService.java:52-60, 145-148` |

---

## 5. 🟢 LOW (texnik qarz, qisqacha)

| Kategoriya | Soni | Asosiy misollar |
|---|---|---|
| Admin xavfsizligi | 9 | Lockout orqali email enumeration; TOTP secret DB va backupda ochiq matnda; HSTS yo'q; SUPPORT admin grant so'rovlari bilan sotuvchilarni spam qila oladi; `/sellers` paging int overflow → 500; `spring-security-crypto` 6.3.4 CVE-2025-22228 (BCrypt >72 bayt), parol uzunligiga yuqori chegara yo'q |
| Bot / Telegram | 14 | Update qayta ishlashdan **oldin** "processed" deb belgilanadi (at-most-once: crash yoki redeployda xabar yo'qoladi); graceful shutdown yo'q (`stop()` chaqirilmaydi); ikkinchi instansiya texnik jihatdan bloklanmagan; 4096 belgi limiti tekshirilmaydi; soxta callback data ERROR stacktrace chiqaradi; xavfli tasdiqlash tugmalarida nonce yo'q; guruh chatdagi javoblar rate-limiterni chetlab o'tadi |
| Hisob-kitob | 8 | `"0,075"` minglik ajratgich deb o'qilib **75%** ga aylanadi; `PlanService.isAllowed` `LIMIT_APPLIED`ni cheksiz deb hisoblaydi (limitlar ishlamaydi); `RoundingMode.UNNECESSARY` ko'p kasrli kiritishda `ArithmeticException` berishi mumkin; tavsiya narxi ba'zan 1 qadam yuqori chiqadi |
| Ma'lumotlar bazasi | 11 | `users`, `access_grants`, `admins` jadvallarida `@Version` yo'q (lost update); DeletionJob barcha akkauntlarni bitta tranzaksiyada yopadi; purge yo'lidagi FK'larda indeks yo'q; DB tranzaksiyasi ichida Telegram HTTP chaqiruvi; `audit_events` cheksiz o'sadi; Hikari pool va timeoutlar standart holatda; admin grant ro'yxatida N+1 |
| DevOps / CI | 9 | Docker log rotatsiyasi yo'q; CI Docker image build, lint va dependency scan qilmaydi; production hostda build qilinadi (versiyalangan image va rollback yo'q); README'da mavjud bo'lmagan `ADMIN_API_KEY` tilga olingan; `TERMS_VERSION` konteynerga uzatilmaydi; lokal PG 14 / CI va prod PG 16 |
| Admin UI | 11 | Grant muddati tugagach ham tijoriy ma'lumot ekranda qoladi; formalarda busy holat yo'q (ikki marta bosish = ikki so'rov); `index.html` uchun cache siyosati yo'q; `vite` (dev, high) va `react-router` (moderate) advisorylari; mobil menyuda Profil havolasi yo'q; `useLoad`da request cancellation yo'q |
| Maxfiylik / UX | 12 | Shartlar versiyasi matnga bog'lanmagan; `DELETION_PURGE_DATA` keyin yoqilsa, eski CLOSED akkauntlar tozalanmaydi; bloklash grantlarni DBda bekor qilmaydi; onboardingda "orqaga" tugmasi yo'q; audit USER harakatlarida user ID yo'q |
| Testlar | 9 | DeletionJob purge=true tarmog'i bajarilmagan; rate-limit va xato yo'llari testlanmagan; `IntegrationTest` `SELLERFLOW_TEST_DB_URL` ko'rsatgan **har qanday** bazadagi jadvallarni TRUNCATE qiladi; Maven enforcer/toolchain yo'q |

> To'liq ro'yxat (113 ta tasdiqlangan topilma, dalil va tekshiruvchi xulosalari bilan) xom natija faylida: `C:\Users\Lenovo\AppData\Local\Temp\claude\e4ff3b5241f82ab4\2f8e8d73-1dc9-456e-95c1-34fff295e007\scratchpad\result.json`

---

## 6. ✅ Kuchli tomonlar (tasdiqlangan)

- **Hisob-kitob:** barcha pul va foiz amallari `BigDecimal` bilan, `double` yo'q. Satrlar yig'indisi jami summaga doim teng. Nolga bo'lish har joyda himoyalangan. 200 000 holatli fuzz testda har bir OK natija maqsadga yetdi.
- **Tenant izolyatsiyasi:** `findByIdAndBusinessId` izchil ishlatiladi. Boshqa biznesning idempotency kalitlari rad etiladi.
- **Bot himoyasi:** faqat private chat; `update_id` dedup DB PK bilan; callbacklarda revision va step bilan stale-button himoyasi; token loglarga tushmaydi.
- **DB:** constraintlar domenga mos (bitta user → bitta biznes, SKU biznes ichida noyob). Indekslar asosiy so'rovlarga mos. Saqlangan tarix snapshoti `updatable=false`.
- **Admin:** BCrypt cost 12, dummy-hash orqali timing himoyasi; session tokeni 256-bit va DBda faqat SHA-256 hashi; majburiy 2FA; har bir so'rovda rol DBdan qayta o'qiladi; rozilik asosidagi grant modeli yaxshi loyihalangan.
- **Infra:** konteyner non-root, multi-stage build; Postgres tashqariga ochilmagan; app faqat `127.0.0.1`; `${VAR:?}` bilan majburiy sirlar; atomar backup yozish (`.tmp` → `mv`); restore uchun "yes" tasdiqlash va alohida restore drill.
- **Admin UI:** XSS manbalari yo'q, inline script yo'q (CSP bilan mos), token `localStorage`da emas `sessionStorage`da, QR kod mijoz tomonida yaratiladi.

---

## 7. Build va test natijalari (amalda bajarildi, repo nusxasida)

| Buyruq | Natija |
|---|---|
| `mvn -B test` (lokal JDK 17.0.12) | ❌ `release version 21 not supported`. Kutilgan holat, loyiha Java 21 talab qiladi |
| `mvn -B test` (JDK 21.0.12) | ✅ **BUILD SUCCESS: 79 test, 0 xato, 0 o'tkazib yuborilgan** (5:36 min, embedded PG, Flyway V1+V2) |
| JaCoCo qamrovi | Satrlar **77.9%** (2482/3186), tarmoqlar **59.6%**. `TelegramPoller` 0%, `HttpTelegramGateway` 58% |
| `javac -Xlint:all` | 0 xato, 6 ta arzimas ogohlantirish (`[serial]`, `[this-escape]`) |
| `npm ci && npm run build` (admin-ui) | ✅ JS 235 kB (gzip 76 kB) |
| `npm run lint` | ✅ 0 muammo |
| `npm audit` | 11 ta zaiflik (1 high: `vite` dev-server; moderate: `react-router` open redirect). `--omit=dev` bilan 2 ta moderate |

---

## 8. Rad etilgan da'volar (adversarial tekshiruvdan o'tmagan)

1. Buzilgan flow JSON foydalanuvchini butunlay "o'chirib qo'yadi"
2. Uzilgan poller oqimi uxlamasdan aylanadi
3. `application.yml`da zaif fallback DB paroli
4. Uzun `TERMS_VERSION` onboardingni jim bloklaydi
5. Stacktrace'lar foydalanuvchi qiymatlarini loglarga chiqaradi

---

## 9. Harakat rejasi

### P0: darhol (bugun)
1. **C-1:** sirlarni `.env.example`dan olib tashlang. Fayl biror joyga ketgan bo'lsa, tokenni revoke qiling va parollarni almashtiring. Secret-scan hook qo'shing.

### P1: public launchgacha (1–2 hafta)
2. **H-1:** admin auth'ni Spring Security yoki HandlerInterceptor'ga o'tkazing, bypass URLlari uchun test yozing.
3. **H-2 / M-15 / M-17:** backup alerti, freshness healthcheck, xavfsiz rotatsiya, off-site va shifrlangan nusxa.
4. **H-3:** yurist tasdiqlagan shartlar va privacy policy.
5. **M-1, M-2, M-3, M-4, M-5:** TOTP replay himoyasi, IP rate-limit, atomik hisoblagichlar, parol o'zgarganda sessiyalarni bekor qilish, server tomonda majburiy parol almashtirish.
6. **M-19:** Spring Boot'ni qo'llab-quvvatlanadigan 3.4.x/3.5.x ga yangilang (CVE yamoqlari).
7. **M-23 / M-24 / M-25:** o'chirish siyosatini aniq va to'g'ri qiling, CLOSED foydalanuvchiga qayta ro'yxatdan o'tish yo'lini bering.

### P2: launchdan keyin (1 oy)
8. Telegram ishonchliligi: 429 / `retry_after`, outgoing retry, bot holatini aks ettiruvchi health indicator, Docker healthcheck, graceful shutdown, at-least-once qayta ishlash.
9. Observability: Micrometer + Prometheus, alertlar.
10. Testlar: `TelegramPoller`, admin boshqaruvi, auth bypass regression, DeletionJob purge.
11. CI: Docker image build, admin-ui lint, `npm audit` / OWASP dependency-check, shellcheck, Maven enforcer (Java 21).
12. Konteyner xotira limitlari va log rotatsiyasi.

### P3: texnik qarz
13. Qolgan LOW topilmalar (5-bo'lim).

---

## 10. Cheklovlar

- Audit statik tahlil, testlarni nusxada ishga tushirish va bitta offline PoC (H-1) bilan cheklandi. Real serverda yuklama yoki penetratsion test o'tkazilmadi.
- Docker lokal mashinada o'rnatilmagan, shuning uchun `docker compose` stack ishga tushirilmadi.
- Tanqidchi topgan 7 ta qo'shimcha topilma ikkinchi adversarial bosqichdan o'tmagan. C-1 va backup rotatsiyasi muammosi qo'lda tasdiqlandi.
- Huquqiy baholar (H-3, M-23) yurist xulosasi o'rnini bosmaydi.
