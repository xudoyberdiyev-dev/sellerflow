# SellerFlow: Qayta audit (Re-audit) hisoboti

| | |
|---|---|
| **Sana** | 2026-11-07 |
| **Obyekt** | `main` @ `b2a8b34` (oldingi audit: `f4a1d6e`, 2026-11-06). Oraliqda 45 ta commit, 309 ta fayl, +21 310 / −585 qator |
| **Usul** | Faqat o'qish. Kod o'zgartirilmagan. Build va testlar repo nusxasida bajarilgan |
| **Tekshiruvchilar** | 6 guruh oldingi topilmalarni qayta tekshirdi, 6 auditor yangi kodni ko'rib chiqdi, har bir topilmani 1–2 adversarial tekshiruvchi tekshirdi, oxirida tanqidchi ishladi. Jami 141 agent |
| **Asos** | [`PRODUCTION_AUDIT_2026-11-06.md`](PRODUCTION_AUDIT_2026-11-06.md), [`docs/AUDIT_PROGRESS.md`](docs/AUDIT_PROGRESS.md). Tracker'dagi belgilarga ishonilmadi, har bir band kod bo'yicha tekshirildi |

> Eslatma: `origin/main` hozir `6f50304` da, bu lokal repoda yo'q. Ya'ni GitHub'da bu audit ko'rmagan yangi commitlar bor.

---

## 1. Yakuniy xulosa

### Hukm: ✅ Yopiq beta uchun **TAYYOR**. ⚠️ Ommaviy (public) launch uchun **2 ta bloker qoldi**: sirlarni almashtirish va huquqiy hujjatlar

Loyiha oldingi auditdan beri **sezilarli darajada yaxshilangan**. Kritik va asosiy xavfsizlik muammolari yopilgan. Qaysi joyga tegilgan bo'lsa, o'sha joy uchun regression test yozilgan. Ishonchlilik, monitoring va backup asosan joyiga qo'yilgan. Qolgan muammolarning ko'pi yangi kod (CSV eksport, sotuvchi kartasi, parallel worker'lar, restore skriptlari) bilan bog'liq kichik va o'rta darajadagi chekka holatlar.

| Ko'rsatkich | 2026-11-06 | **2026-11-07** | O'zgarish |
|---|---|---|---|
| Umumiy baho | 6.5 / 10 | **8.0 / 10** | ⬆ +1.5 |
| 🔴 CRITICAL | 1 | **0** | ✅ yopildi (lekin 1.1-bandga qarang) |
| 🟠 HIGH | 3 | **2** | H-1 yopildi. H-2 MEDIUM'ga tushdi. H-3 ochiq. C-1 qoldig'i HIGH |
| 🟡 MEDIUM | 31 | **12** (unikal) | ⬇ −19 |
| 🟢 LOW | ~84 | **~75** (unikal, asosan yangi kod) | ≈ |
| Testlar | 79 ✅ | **142 ✅** (0 xato) | ⬆ +63 |
| Satr qamrovi | 77.9% | o'lchanmadi | — |
| `npm audit --omit=dev` | 2 moderate | **0** | ✅ |

### Oldingi topilmalar qanday hal qilindi (78 band)

| Holat | Soni | Ulushi |
|---|---|---|
| ✅ FIXED (to'liq tuzatilgan) | **47** | 60% |
| 🟨 PARTIAL (qisman, bo'shliq qolgan) | **23** | 30% |
| ❌ NOT_FIXED (tuzatilmagan) | **8** | 10% |
| ⛔ REGRESSED (yomonlashgan) | **0** | 0% |

### Yo'nalishlar bo'yicha baho

| Yo'nalish | Oldin | Hozir | Izoh |
|---|---|---|---|
| Admin panel xavfsizligi | 5 | **8.5** | Bypass yopildi, TOTP replay yopildi, IP rate-limit bor, temp-parol serverda cheklangan, break-glass bor |
| Ishonchlilik / Telegram | 6 | **8** | Parallel worker'lar, at-least-once, 429/5xx retry, bot health, graceful shutdown |
| DevOps / Backup | 5 | **7.5** | Xavfsiz rotatsiya, status.json, gpg, healthcheck, log rotatsiyasi, CI kengaytirilgan. Restore chekka holatlari qolgan |
| Ma'lumotlar bazasi | 7.5 | **8.5** | `@Version`, FK indekslari, per-account tranzaksiya, audit retention |
| Hisob-kitob | 9 | **9.5** | "0,075", UNNECESSARY, eng kichik qadam va LIMIT_APPLIED tuzatilgan |
| Testlar / CI | 7 | **8.5** | 142 test. Poller va admin boshqaruvi testlangan. gitleaks, shellcheck, docker build |
| Admin UI | 8 | **7.5** | Qayta dizayn yangi kamchiliklar olib keldi: error boundary yo'q, dark mode buzilgan, bundle 303 kB |
| Maxfiylik / huquqiy | 5 | **6** | O'chirish oqimi yaxshilandi, lekin shartlar hali qoralama va privacy policy yo'q |

---

## 2. 🟠 Public launchgacha hal qilinishi shart (HIGH)

### 1.1 C-1 qoldig'i: sirlar ommaviy GitHub tarixida, parollar hali almashtirilmagan
- **Holat:** PARTIAL. Ishchi daraxt toza. `scripts/check-env-example.sh`, pre-commit hook va CI'dagi gitleaks qo'shilgan, bular yaxshi.
- **Muammo:** `9ad8251` commitida (`.env.example`) haqiqiy bot tokeni, DB paroli, superadmin email va paroli turibdi. Bu commit `origin/main` tarixida. GitHub repozitoriysi **ommaviy**: API autentifikatsiyasiz 200 qaytaradi. gitleaks ataylab faqat `292cd7b..HEAD` oralig'ini tekshiradi. `docs/AUDIT_PROGRESS.md:78` da bot tokeni revoke qilingani yozilgan, lekin buni kod orqali tasdiqlab bo'lmaydi. `:79` da **DB va superadmin parollarini almashtirish hali ochiq** (`- [ ]`).
- **Ta'sir:** Superadmin paroli ommaviy. Uni hozir faqat 2FA va IP rate-limit himoya qilmoqda. Agar bot tokeni haqiqatan revoke qilinmagan bo'lsa, botni boshqa odam boshqarishi mumkin.
- **Tavsiya:**
  1. Superadmin parolini panel orqali va DB parolini darhol almashtiring.
  2. Eski token haqiqatan bekor qilinganini tekshiring: @BotFather'da yangi token chiqarilgan bo'lishi kerak.
  3. Hammasi almashtirilgach, tarixni qayta yozish yoki yozmaslik haqida ongli qaror qabul qiling. Qayta yozilsa, GitHub Support'dan cache'ni tozalashni so'rang.
  4. `PRODUCTION_AUDIT_2026-11-06.md:47` da sizib chiqqan qiymatlarning 4 belgilik prefikslari qolgan. Ularni olib tashlang. Bu oldingi hisobotdagi mening xatoim.

### 1.2 H-3: Xizmat shartlari hali qoralama, privacy policy yo'q (NOT_FIXED)
- `src/main/resources/bot/terms_uz.txt:1` hali ham "⚠️ QORALAMA v0.1 — yurist tekshiruvidan o'tmagan" bilan boshlanadi. Fayl o'zgarmagan. `src/main` ichida maxfiylik siyosati umuman yo'q.
- Mavjud matn real xatti-harakatni ham noto'g'ri ifodalaydi: `DELETION_PURGE_DATA=false` holatida tijoriy ma'lumot saqlanib qoladi, audit 365 kun saqlanadi.
- **Tavsiya:** yurist tasdiqlagan shartlar va privacy policy kerak. Ular operator, kontakt, ma'lumot toifalari, maqsad, saqlash muddati, o'chirish tartibi va O'RQ-547 lokalizatsiya talabini qamrab olsin. Keyin `TERMS_VERSION` ni yangilang.

---

## 3. ✅ Nimalar bartaraf qilindi

### Kritik va yuqori
| ID | Muammo | Holat | Dalil |
|---|---|---|---|
| **C-1** | `.env.example`da sirlar | 🟨 Fayl toza, himoya qo'shilgan. Tarix va rotatsiya qolgan | `check-env-example.sh`, `.pre-commit-config.yaml`, CI gitleaks |
| **H-1** | Admin API auth bypass | ✅ **To'liq** | `RequestFirewall` (`;`, `%2f`, `%2e`, `//`, `..` → 400) + `AdminAuthInterceptor` (deny-by-default, MVC path pattern). Auditor HEAD, OPTIONS, trailing slash, katta-kichik harf, `/error` va yangi D-5 endpointlarini tekshirdi, bypass topilmadi. Test: `AdminApiIT.authBypassViaPathTricks_isRejected` (17 yo'l) |
| **H-2** | Backup jim xato qiladi | 🟨 Asosiy qismi bajarilgan | Rotatsiya faqat muvaffaqiyatdan keyin ishlaydi, kamida 7 nusxa saqlanadi, `status.json`, `BackupMonitor` health, Telegram alert, compose healthcheck. Qolgan bo'shliq 4-bo'limda |

### O'rta darajadagilar (tanlab)
| ID | Nima qilindi | Holat |
|---|---|---|
| M-1 | TOTP replay: `totp_last_step` + atomik band qilish (V3) | ✅ |
| M-2/M-3 | IP rate-limit (5/15 daqiqa), atomik xato hisoblagichi, lockout parolni oshkor qilmaydi | ✅ / 🟨 (TOCTOU poygasi qolgan) |
| M-4 | Parol o'zgarganda boshqa sessiyalar bekor qilinadi | ✅ |
| M-5 | Vaqtinchalik parol bilan sessiya serverda cheklangan (`PASSWORD_CHANGE_REQUIRED`) | 🟨 Xuddi shu parolni qayta kiritsa ham qabul qiladi |
| M-6 | `ADMIN_LOGIN_FAILED`, `ADMIN_LOCKED` + manba IP (V4) | ✅ |
| M-7/M-8 | Break-glass tiklash (`ADMIN_RECOVERY_*`, V5); public HTTPS uchun majburiy IP allowlist | ✅ |
| M-9 | Foydalanuvchi bo'yicha parallel worker'lar (`TELEGRAM_WORKERS=4`) | 🟨 Partiya darajasidagi to'siq qolgan |
| M-10 | At-least-once, DB uzilganda offset surilmaydi, `@PreDestroy` | ✅ |
| M-11 | 429 `retry_after`, 5xx retry, metrikalar | ✅ |
| M-12/M-13 | `BotHealthIndicator`, app healthcheck, Prometheus (faqat ichki tarmoq) | ✅ |
| M-14 | Surrogate-xavfsiz qirqish, 4096 bo'yicha bo'lish | ✅ |
| M-15/M-16 | gpg shifrlash, rclone misoli, 600 huquq | 🟨 Shifrlash standartda **o'chiq**, off-site avtomatlashtirilmagan |
| M-17/M-20 | Xavfsiz rotatsiya; xotira limitlari | ✅ |
| M-18 | `restore.sh`: snapshot, `--single-transaction`, `--exit-on-error` | 🟨 Yangi sxemadagi obyektlar qoladi |
| M-19 | Spring Boot 3.3.5 → **3.5.16** (CVE-2025-22228 yopildi) | 🟨 3.5.x ham bepul OSS muddatidan o'tgan |
| M-21/M-22 | Admin boshqaruvi testlari, `TelegramPollerTest` (9 test) | ✅ |
| M-23–M-26 | Haqqoniy o'chirish matni, bot ichida bekor qilish, CLOSED → yangi akkaunt, onboardingda o'chirish, «Ortga» tugmasi, psevdonim | ✅ / 🟨 |

### Past darajadagilar
HSTS, paging overflow → 400, SUPPORT grant limiti, log rotatsiyasi, CI (docker build, lint, shellcheck, npm audit), Maven enforcer, `_test` bazasi himoyasi, terms hash (V6), bloklashda grantlarni bekor qilish, USER `actor_id`, `@Version` va FK indekslari (V7), audit retention, N+1, `index.html` no-cache, vite 7 / react-router 7, mobil Profil, AbortController: hammasi ✅.

### Hali ham tuzatilmagan (NOT_FIXED, 8 ta)
H-3 (shartlar va privacy policy); lockout orqali email enumeration; TOTP secret ochiq matnda; ikkinchi instansiya texnik bloklanmagan; xavfli tasdiqlash tugmalarida nonce yo'q; guruh chatlari rate-limiterni chetlab o'tadi; versiyalangan image va rollback yo'q; lokal PG 14 / prod PG 16.

---

## 4. 🟡 Yangi va qolgan MEDIUM muammolar (12 ta unikal)

| # | Muammo | Joy | Tavsiya |
|---|---|---|---|
| N-1 | **TermsGuard xeshi qator oxirlarini normallashtirmaydi.** `.gitattributes` faqat `*.sh` uchun LF'ni majburlaydi. Windows'da `autocrlf=true` bo'lsa, `terms_uz.txt` CRLF bo'lib qoladi va xesh boshqacha chiqadi (auditor: `4476e8af…` va `456b0d85…`). Natijada Windows'da build qilingan image **ishga tushmaydi** | `account/TermsGuard.java:36-47` | Xeshdan oldin `\r\n` → `\n` qiling. `.gitattributes`ga `*.txt text eol=lf` qo'shing |
| N-2 | **`status.json` yo'q bo'lsa, backup holati UNKNOWN → health UP, alert yo'q.** Backup konteyneri umuman ishga tushmasa ham (masalan, CRLF xatosi) tizim jim turadi | `ops/BackupMonitor.java:60, 81, 92` | Ilova ishga tushgandan N soat o'tib ham `status.json` bo'lmasa, holatni STALE yoki DOWN deb hisoblang |
| N-3 | Backup alerti standartda o'chiq: `ALERT_TELEGRAM_CHAT_ID` bo'sh, startupda ogohlantirish ham chiqmaydi | `BackupMonitor.java:90`, `.env.example:41` | Startupda WARN chiqaring, OPERATIONS'da majburiy deb yozing |
| N-4 | **`restore-drill.sh` `.dump` va `.dump.gpg` ikkalasi ham bo'lmasa, jimgina exit 2 qiladi.** Oylik tiklash sinovi aslida ishlamaydi (soxta docker bilan takrorlandi) | `scripts/restore-drill.sh:5` | `ls` glob xatosini to'g'ri ishlang (`nullglob`) |
| N-5 | **`restore.sh` eski sxemadagi backupni tiklaganda "Tiklandi" deydi, lekin app Flyway xatosi bilan ishga tushmaydi.** Muvaffaqiyatsiz upgrade'dan keyin rollback qilish ssenariysi ishlamaydi | `scripts/restore.sh:41-42, 148-157` | Tiklashdan oldin sxemani tozalang (`DROP SCHEMA public CASCADE`) yoki Flyway versiyasini tekshiring. Rollback tartibini hujjatlashtiring |
| N-6 | `LoginRateLimiter` check va record atomik emas: parallel so'rovlar bilan bitta IP limitdan oshib, adminni lockout qila oladi | `admin/AdminController.java:101-110`, `LoginRateLimiter.java` | Urinishni oldindan band qiling (increment-then-check) |
| N-7 | **Poller oqimi o'lsa yoki JVM'da OOM bo'lsa, avtomatik tiklanish yo'q.** Healthcheck faqat holatni ko'rsatadi. Bundan tashqari, transient retry'da yuqori chegara yo'q: bitta "zaharli" update butun botni to'xtatib qo'yadi | `telegram/TelegramPoller.java:87-99, 140, 147-158` | `-XX:+ExitOnOutOfMemoryError`; poller o'lsa qayta ishga tushirish yoki `System.exit`; retry limiti va dead-letter qo'shing |
| N-8 | **Spring Boot 3.5.16 ning bepul OSS qo'llab-quvvatlash muddati (2026-06) o'tgan.** CI'da Java bog'liqliklari uchun CVE skaner yo'q (`versions` hisoboti faqat ma'lumot uchun, `\|\| true`) | `pom.xml:9-10`, `ci.yml` | Spring Boot 4.x ga o'tishni rejalashtiring. OWASP dependency-check, Trivy, osv-scanner yoki Dependabot qo'shing |
| N-9 | **Qayta ochilgan (reopen) CLOSED akkauntlarning eski tijoriy ma'lumoti hech qachon o'chirilmaydi.** `purgePreviouslyClosed` `telegram_user_id > 0` filtri tufayli ularni o'tkazib yuboradi, support ham ularni topa olmaydi | `account/DeletionJob.java:90, 358`, `AccountService.java:209` | Detach paytida ma'lumotni purge navbatiga qo'ying yoki filtrni tuzating |
| N-10 | O'chirish ekranida «to'liq o'chirishni qo'llab-quvvatlash orqali so'rang» degan va'da bor, lekin buning mexanizmi yo'q, support kontakti esa ixtiyoriy | `bot/Texts.java:268-270` | Admin panelga per-account purge amalini qo'shing yoki va'dani olib tashlang |
| N-11 | M-26 qoldig'i: `users` yozuvi rozilikdan **oldin**, hatto `/help` bosilganda ham yaratiladi. Tugallanmagan onboarding yozuvlari haqiqiy Telegram ID bilan muddatsiz qoladi | `bot/BotDispatcher.java:122-126`, `AccountService.java:64-74` | Yozuvni faqat rozilikdan keyin yarating yoki N kunlik retention bering |
| N-12 | M-9 qoldig'i: partiya darajasidagi to'siq. Eng sekin foydalanuvchi (45 s read timeout × 3 urinish) keyingi partiyani **hamma uchun** ushlab turadi va bot health'ni soxta DOWN ko'rsatadi | `TelegramPoller.java:126-143` | Har bir worker'ga doimiy navbat bering, partiyalarni bir-birini kutmaydigan qiling, sendMessage timeout'ini qisqartiring |

---

## 5. 🟢 Yangi LOW muammolar (qisqacha, takrorlar birlashtirilgan)

| Kategoriya | Asosiy misollar |
|---|---|
| **Yangi admin API (D-5)** | CSV formula injection'ni `;` va tab orqali chetlab o'tish mumkin; audit CSV'ga autentifikatsiyasiz kiritilgan matn tushadi; eksport jimgina 10 000 qatorda kesiladi; **SUPPORT roli butun sotuvchilar bazasini (Telegram ID) CSV qilib eksport qila oladi**; sotuvchi kartasi SUPPORT'ga roziliksiz biznes nomi va ismni, hamda faqat SUPERADMIN'ga mo'ljallangan audit sababini ko'rsatadi, karta ko'rishlari auditlanmaydi; ekstremal ISO sanalar 500 beradi; audit filtrlari indekssiz; CSV vaqti UTC'da, offsetsiz |
| **Admin auth** | `changePassword` xuddi shu parolni qabul qiladi (M-5 ni chetlab o'tish); bootstrap paroli 72 baytdan oshsa, startup tushadi; `/auth/totp/confirm` `setupToken=null` bo'lsa 500 beradi; `confirmTotp` lockoutni hisobga olmaydi; imtiyozli amallar uchun step-up yo'q (o'g'irlangan SUPERADMIN sessiyasi bilan backdoor admin yaratish mumkin); `forward-headers-strategy=native` har qanday private IP'dan kelgan XFF'ga ishonadi; SSH-tunnel rejimida IP docker gateway bo'lib ko'rinadi; ochiq auth endpointlarida so'rov tanasi hajmi cheklanmagan |
| **Konkurrentlik** | `@Version` qo'shilgach, `OptimisticLockingFailureException` admin API'da 500 bo'ladi; native UPDATE'lar `version`ni oshirmaydi, to'liq JPA save `totp_last_step`ni ustidan yozib yuborishi mumkin; `BotStatus.processed++` atomik emas; worker'dagi `Error` partiyani cheksiz qayta uradi; DeletionJob SELECT'dan keyin holatni qayta tekshirmaydi (TOCTOU); `purgePreviouslyClosed`da per-account try/catch yo'q; graceful shutdown 10 s kutadi, worker'lar esa ~136 s ishlashi mumkin; SUPPORT grant limiti count-then-insert |
| **Telegram** | Idempotent bo'lmagan `sendMessage` read-timeout'dan keyin qayta yuboriladi (dublikat xabarlar); `splitForTelegram` HTML teg ichidan kesishi mumkin; IllegalArgumentException hali ham ERROR stacktrace beradi |
| **Admin UI (qayta dizayn)** | **Error boundary yo'q** (render xatosi = oq ekran); dark mode tugmasi production'da ochiq, lekin sarlavhalar ko'rinmay qoladi; grant bekor qilish hali `window.confirm` bilan, useBusy yo'q (ikki marta bosish ikki audit yozuvi beradi); grant revoke qilinsa ham ma'lumot ekranda qoladi; 2FA setup'dan orqaga qaytish yo'q; `/auth/me.expiresAt` ishlatilmaydi; OtpInput'da raqamlar siljiydi; sidebar va ⌘K'da fokus nazorati yo'q; ikki parallel UI to'plami, bundle 235 → **303 kB**; Tailwind preflight `base.css` uslublarini bekor qiladi; yangi D-5 API'lar UI'da ishlatilmaydi; JSON bo'lmagan xato javobida (502/413) `SyntaxError` |
| **DevOps / CI** | `DB_POOL_SIZE`, `AUDIT_RETENTION_DAYS`, `TELEGRAM_WORKERS`, `BACKUP_INTERVAL_SECONDS` compose orqali konteynerga uzatilmaydi; 768 MB'da 75% heap, non-heap uchun ~190 MB qoladi; pre-restore snapshot shifrlanmagan va rotatsiya qilinmaydi; backup root:600, hujjatlardagi buyruqlar esa oddiy user'ga mo'ljallangan; update tartibi backup konteynerini qayta yaratmaydi; gitleaks binari checksum'siz, action'lar SHA bilan pinlanmagan, `permissions:` yo'q; base image'lar floating tag; passphrase va DB paroli `docker inspect`da ko'rinadi; OPERATIONS §6 bilan §4B allowlist zid |
| **Maxfiylik / UX** | Admin foydalanuvchining o'chirish so'rovini bildirmasdan bekor qila oladi; SUSPENDED foydalanuvchi o'chirishni so'ray olmaydi; o'chirish backup va snapshot'larga tarqalmaydi; onboardingda `/cancel` kabi buyruqlar biznes nomi sifatida saqlanadi; `/delete` hech qayerda e'lon qilinmagan; psevdonimlangan yozuvlar ro'yxatda ko'rinadi; o'chirish so'rovi faol grantlarni bekor qilmaydi; grant orqali faqat birinchi 10 ta mahsulot ko'rinadi; sotuvchi botda faol ruxsatlarini ko'ra olmaydi; `TERMS_VERSION` uzunligi va `:` tekshirilmaydi; `SUPPORT_URL` noto'g'ri bo'lsa startup tushadi |
| **Hujjatlar** | `CLAUDE.md` troubleshooting bo'limida `HTTP 401/409` logda chiqadi deyilgan, lekin poller endi HTTP statusini loglamaydi |

> To'liq xom natija (har bir topilma dalil va tekshiruvchi xulosasi bilan): `C:\Users\Lenovo\AppData\Local\Temp\claude\e4ff3b5241f82ab4\2f8e8d73-1dc9-456e-95c1-34fff295e007\scratchpad\reaudit.json`

---

## 6. Build va test natijalari (repo nusxasida bajarildi)

| Buyruq | Natija |
|---|---|
| `mvn -B verify` (JDK 21) | ✅ **BUILD SUCCESS: 142 test, 0 xato, 0 o'tkazib yuborilgan**. Maven enforcer: Java 21+ ✅, Maven 3.9+ ✅ |
| `AdminApiIT` | 34/34 ✅ |
| `TelegramPollerTest` | 9/9 ✅ |
| `npm ci && npm run build` | ✅ vite 7.3.7. JS **302.77 kB** (gzip 99.42 kB). Oldin 235 kB edi |
| `npm run lint` | ✅ toza |
| `npm audit --omit=dev` | ✅ **0 zaiflik**. Dev'da 7 ta moderate (minimatch) |

---

## 7. Harakat rejasi

### P0: bugun
1. Superadmin va DB parollarini almashtiring. Eski bot tokeni bekor qilinganini tasdiqlang (1.1).
2. `PRODUCTION_AUDIT_2026-11-06.md:47` dagi prefikslarni olib tashlang.
3. **N-1:** TermsGuard'da CRLF normalizatsiyasi va `.gitattributes`ga `*.txt eol=lf`. Aks holda Windows'dagi Docker build ishga tushmaydi.

### P1: public launchgacha
4. **H-3:** yurist tasdiqlagan shartlar va privacy policy. Matn real retention siyosatiga mos bo'lsin (N-9, N-10, N-11).
5. Backup monitoringini to'ldiring (N-2, N-3) va restore skriptlarini tuzating (N-4, N-5). Tiklash sinovini real muhitda bir marta bajarib ko'ring.
6. **SUPPORT rolini cheklang:** CSV eksport va sotuvchi kartasidagi ism va biznes nomi faqat SUPERADMIN'ga yoki grant orqali ochiq bo'lsin, ko'rishlar auditlansin. CSV injection'ni tuzating.
7. N-6 (rate-limit poygasi), M-5 qoldig'i (xuddi shu parol), `OptimisticLockingFailureException` → 409.
8. Admin UI'ga error boundary qo'shing.

### P2: launchdan keyin
9. N-7 / N-12: poller'ning o'zini tiklashi, retry limiti, partiya to'sig'ini olib tashlash, `-XX:+ExitOnOutOfMemoryError`.
10. N-8: Spring Boot 4.x rejasi va CI'da CVE skaner.
11. Compose'ga tuning env'larini qo'shing. Shifrlashni standart holatda yoqing. Versiyalangan image va rollback.
12. UI qarzi: dark mode, bundle hajmi, eski UI to'plamini olib tashlash, D-5 API'larni ulash.

---

## 8. Cheklovlar
- Statik tahlil, nusxada build va test, bir nechta offline takrorlash (restore-drill, csvCell, TermsGuard xeshi). Ilova real muhitda ishga tushirilmadi: Docker stack ham, yuklama testi ham, penetratsion test ham o'tkazilmadi.
- Bot tokeni haqiqatan revoke qilinganini tekshirish uchun tokendan foydalanish kerak edi. Bu qilinmadi va qilinmasligi kerak.
- 4 ta da'vo adversarial tekshiruvda rad etildi va hisobotga kiritilmadi.
- `origin/main` dagi `6f50304` (lokalda yo'q) audit qilinmadi.
- Huquqiy baholar yurist xulosasi o'rnini bosmaydi.
