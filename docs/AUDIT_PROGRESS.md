# Audit tuzatishlari — ish rejasi va progress

Manba: `PRODUCTION_AUDIT_2026-11-06.md`. Ish har kuni **11:30–13:30 (Asia/Tashkent)** da avtomatik sessiyada davom etadi.
Har sessiya shu fayldan **birinchi belgilanmagan bandni** oladi va tartib bilan davom etadi (zanjir).
Bajarilgan band `- [x]` bilan belgilanadi; qisman bajarilgani `- [~]` va izohda nima qolgani yoziladi.

## Qoidalar (har sessiya uchun)
- Ish faqat `main` ga, kichik mantiqiy commitlar bilan. **Force push yo'q.** Tarixni qayta yozish yo'q.
- Har commitdan oldin: `mvn -B verify` (barcha testlar) va UI o'zgarsa `cd admin-ui && npm run build && npm run lint`. Test qizil bo'lsa — commit qilinmaydi.
- Har tuzatilgan xavfsizlik muammosi uchun **regression test** yoziladi.
- Sirlar (token, parol) hech qachon repoga yozilmaydi. `.env` o'qilmaydi.
- Hisob formulalari (`CalculationEngine`) xatti-harakati o'zgarsa — `CalculationEngineTest` yangilanadi va sabab `docs/DECISIONS.md` ga yoziladi.
- Har sessiya oxirida (13:15 dan kechiktirmay): shu fayl yangilanadi (sessiya jurnali + belgilar), push qilinadi.

## 1-bosqich — Xavfsizlik (eng muhim)
- [ ] **C-1 (qolgani):** gitleaks secret-scan — CI'ga qadam + `.pre-commit-config.yaml`; README'da eslatma. `.env.example` faqat bo'sh qiymatlar bilan qolishi testlansin (CI'da grep).
- [ ] **H-1:** Admin auth'ni xom URI emas, Spring Security (`SecurityFilterChain`, `requestMatchers("/admin/api/**")`, StrictHttpFirewall) yoki `HandlerInterceptor` + path pattern bilan; deny-by-default. Regression testlar: `/admin/api;x=1/v1/sellers`, `/admin;/api/v1/sellers`, `/admin/%61pi/v1/sellers` → 401/400.
- [ ] **M-1:** TOTP replay himoyasi — admin uchun oxirgi ishlatilgan qadam (`totp_last_step`) saqlansin, shu yoki eskiroq qadam rad etilsin.
- [ ] **M-2 / M-3:** IP bo'yicha login rate-limit; failed/lockout hisoblagichlarini atomik (`UPDATE ... SET failed_attempts = failed_attempts + 1`) qilish; lockout javobi parol to'g'riligini oshkor qilmasin.
- [ ] **M-4:** parol o'zgarganda boshqa barcha sessiyalar bekor; joriy parolni tekshirishda ham urinishlar hisobga olinsin.
- [ ] **M-5:** vaqtinchalik parol bilan sessiya serverda cheklangan (faqat `/auth/me`, `/auth/password`, `/auth/logout`) — almashtirmaguncha boshqa API 403 `PASSWORD_CHANGE_REQUIRED`; UI shunga mos.
- [ ] **M-6:** auditga xavfsizlik hodisalari (ADMIN_LOGIN_FAILED, ADMIN_LOCKED) va manba IP.
- [ ] **M-7:** break-glass: `ADMIN_RECOVERY_EMAIL` + `ADMIN_RECOVERY_PASSWORD` env berilsa va shu email'li admin bloklangan/2FA yo'qotilgan bo'lsa — startupda qayta tiklash (audit bilan), hujjatlash.
- [ ] **M-8:** `docs/OPERATIONS.md` §4B: public HTTPS varianti uchun IP allowlist majburiy deb yozish, Caddy misoliga `remote_ip` cheklovi.
- [ ] **LOW (admin):** HSTS sarlavhasi; parol uzunligi yuqori chegarasi (72 bayt, BCrypt); `/sellers` paging overflow → 400; SUPPORT uchun grant so'rovlariga limit (masalan, 1 sotuvchiga 24 soatda 3 ta).

## 2-bosqich — Backup va ishonchlilik
- [ ] **H-2 / M-17:** `backup-loop.sh`: rotatsiya faqat muvaffaqiyatli backupdan keyin, kamida oxirgi 7 nusxa doim saqlanadi; holat fayli (`/backups/status.json`: lastSuccessAt, lastError) yoziladi; xatoda Telegram alert (`ALERT_TELEGRAM_CHAT_ID`, bot tokeni orqali) — ixtiyoriy env.
- [ ] **H-2 (davomi):** ilova `status.json` ni o'qiydi → admin `/health` va `/actuator/health` da backup holati; compose'da backup healthcheck (oxirgi muvaffaqiyat < 26 soat).
- [ ] **M-15 / M-16:** backupni shifrlash (`BACKUP_ENCRYPTION_PASSPHRASE` berilsa `gpg --symmetric` yoki `age`), fayl huquqlari 600; off-site nusxa uchun `rclone` misoli (`docs/OPERATIONS.md`).
- [ ] **M-18:** `restore.sh`: tiklashdan oldin joriy bazaning xavfsizlik snapshoti; `--single-transaction`; xatoda to'xtash.
- [ ] **M-12:** bot holatini aks ettiruvchi `HealthIndicator` (poller oxirgi muvaffaqiyati < 120 s); `app` uchun Docker healthcheck.
- [ ] **M-20:** compose'da xotira limitlari (`mem_limit`), JVM sozlamasi bilan mos; README/OPERATIONS RAM talablari bir xil.
- [ ] **LOW (DevOps):** Docker log rotatsiyasi (`logging.options.max-size`); README'dagi eskirgan `ADMIN_API_KEY` eslatmasi olib tashlansin; `TERMS_VERSION` compose orqali uzatilsin.

## 3-bosqich — Platforma yangilanishi va Telegram
- [ ] **M-19:** Spring Boot → qo'llab-quvvatlanadigan 3.5.x (yoki 3.4.x); `spring-security-crypto` CVE-2025-22228 yopilsin; barcha testlar yashil.
- [ ] **M-11:** chiquvchi Telegram chaqiruvlari: 429 `retry_after` ni hurmat qilish, 5xx da cheklangan retry (eksponensial), muvaffaqiyatsizlik metrikasi.
- [ ] **M-10 + LOW:** at-least-once: update DB xatosida qayta ishlanishi uchun `processed_updates` belgisi muvaffaqiyatdan keyin (yoki tranzaksiya ichida); DB uzilganda offset oldinga surilmasin; graceful shutdown (`@PreDestroy` → `stop()`).
- [ ] **M-9:** parallel qayta ishlash — foydalanuvchi bo'yicha ketma-ketlik saqlangan holda (masalan, `userId % N` bo'yicha N ta worker navbat); Telegram HTTP chaqiruvlari DB tranzaksiyasidan tashqarida.
- [ ] **M-14 + LOW:** matnni qisqartirish code point bo'yicha (surrogate juftini buzmaslik); 4096 belgi limiti (uzun xabarni bo'lish yoki qisqartirish); soxta callback data — ERROR stacktrace emas, WARN.
- [ ] **M-13:** Micrometer + `/actuator/prometheus` (faqat localhost), asosiy metrikalar: update'lar, xatolar, Telegram latency, hisoblashlar.

## 4-bosqich — Maxfiylik va akkaunt oqimi
- [ ] **M-23:** o'chirish matnlari haqiqatga mos bo'lsin (nima o'chadi / nima qoladi); `DELETION_PURGE_DATA` haqida aniq yozuv; purge yoqilganda Telegram ID ham anonimlashtirilsin (hash) — qaror `docs/DECISIONS.md` ga.
- [ ] **M-24:** CLOSED foydalanuvchi `/start` bosganda yangi akkaunt ochish yo'li (eski ma'lumotsiz), qaror hujjatlansin.
- [ ] **M-25:** o'chirishni kutayotgan foydalanuvchi botning o'zida «O'chirish so'rovini bekor qilish» tugmasi bilan 30 kun ichida tiklay olsin (support'ga bog'liq bo'lmasin).
- [ ] **M-26:** onboardingni tugatmagan foydalanuvchi uchun ma'lumotni o'chirish (`/delete` yoki onboardingda «Bekor qilish va ma'lumotni o'chirish»); onboardingda «Ortga» tugmasi.
- [ ] **LOW (maxfiylik):** shartlar versiyasi matn hash'iga bog'lansin; bloklashda faol grantlar DB'da bekor qilinsin; `DELETION_PURGE_DATA` keyin yoqilsa eski CLOSED akkauntlar ham tozalansin; USER audit yozuvlarida user ID.

## 5-bosqich — Hisob-kitob va DB
- [ ] **LOW (hisob):** `"0,075"` → 0.075 (minglik ajratgich qoidasini aniqlashtirish: faqat vergul+3 raqam va butun qism 1–3 raqam bo'lsa ham kontekstga qarab — foiz maydonlarida minglik ajratgich qabul qilinmasin); `RoundingMode.UNNECESSARY` xavfi yo'qotilsin; `PlanService.isAllowed` `LIMIT_APPLIED` ni to'g'ri talqin qilsin; tavsiya narxi ortiqcha qadam masalasi tekshirilsin.
- [ ] **LOW (DB):** `users`, `access_grants`, `admins` uchun `@Version`; DeletionJob har akkaunt alohida tranzaksiyada; purge yo'lidagi FK'larga indekslar; `audit_events` saqlash muddati (konfiguratsiya, standart 365 kun); Hikari pool/timeout sozlamalari; grant ro'yxatidagi N+1.

## 6-bosqich — Testlar, CI va UI
- [ ] **M-21:** admin boshqaruvi (create/disable/enable/reset-2fa) va parol almashtirish uchun testlar.
- [ ] **M-22:** `TelegramPoller` testlari (offset, backoff, xato ishlash) — soxta gateway bilan.
- [ ] **LOW (testlar):** DeletionJob purge=true; rate-limit yo'llari; `IntegrationTest` faqat nomi `_test` bilan tugaydigan bazada TRUNCATE qilsin (aks holda to'xtasin); Maven enforcer (Java 21).
- [ ] **LOW (CI):** Docker image build, admin-ui lint, `npm audit --omit=dev`, OWASP dependency-check yoki `mvn versions` hisobot, shellcheck.
- [ ] **LOW (UI):** grant muddati tugaganda ma'lumot ekrandan tozalansin; formalarda busy holat (ikki marta yuborish yo'q); `index.html` uchun `Cache-Control: no-cache`; `vite` va `react-router` yangilansin; mobil menyuda Profil; `useLoad` da AbortController.

## 7-bosqich — Yakun
- [ ] Barcha bandlar qayta ko'rib chiqiladi; qolgan LOW topilmalar ro'yxati shu faylga «Qoldirilganlar» sifatida sababi bilan yoziladi.
- [ ] Yakuniy hisobot: `docs/AUDIT_FIX_REPORT.md` (nima tuzatildi, qaysi commit, qaysi test, nima qoldi va nega).

## Foydalanuvchi (loyiha egasi) ishlari — avtomatik sessiya qila olmaydi
- [x] Telegram bot tokenini @BotFather orqali bekor qilish va yangisini olish (2026-11-07).
- [ ] DB va admin parollarini almashtirish (ular ham ochiq repoda ko'ringan edi).
- [ ] H-3: yurist tasdiqlagan xizmat shartlari va maxfiylik siyosati.

## Sessiya jurnali
| Sana | Bajarilgan bandlar | Commitlar | Izoh |
|---|---|---|---|
| 2026-11-07 00:10 | Reja tuzildi; `.env.example` dagi sirlar olib tashlandi (292cd7b) | — | Avtomatik ish 2026-11-07 11:30 dan boshlanadi |
