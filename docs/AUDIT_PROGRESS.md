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
- [x] **C-1 (qolgani):** gitleaks secret-scan — CI'ga qadam + `.pre-commit-config.yaml`; README'da eslatma. `.env.example` faqat bo'sh qiymatlar bilan qolishi testlansin (CI'da grep). — `scripts/check-env-example.sh`, CI `secrets` job (gitleaks 8.21.2, tarix 292cd7b..HEAD — undan oldingi revoked sirlar ataylab tashqarida), `.pre-commit-config.yaml`.
- [x] **H-1:** Admin auth'ni xom URI emas, Spring Security (`SecurityFilterChain`, `requestMatchers("/admin/api/**")`, StrictHttpFirewall) yoki `HandlerInterceptor` + path pattern bilan; deny-by-default. Regression testlar: `/admin/api;x=1/v1/sellers`, `/admin;/api/v1/sellers`, `/admin/%61pi/v1/sellers` → 401/400. — `RequestFirewall` + `AdminAuthInterceptor` + `@PublicAdminEndpoint`; testlar: `AdminApiIT.authBypassViaPathTricks_isRejected`, `RequestFirewallTest`.
- [x] **M-1:** TOTP replay himoyasi — admin uchun oxirgi ishlatilgan qadam (`totp_last_step`) saqlansin, shu yoki eskiroq qadam rad etilsin. — V3 migratsiya, atomik `consumeTotpStep`; test `AdminApiIT.totpCode_cannotBeReplayed`.
- [x] **M-2 / M-3:** IP bo'yicha login rate-limit; failed/lockout hisoblagichlarini atomik (`UPDATE ... SET failed_attempts = failed_attempts + 1`) qilish; lockout javobi parol to'g'riligini oshkor qilmasin. — `LoginRateLimiter` (IP: 5 xato/15 daq → 429), akkaunt lockout 10 ta, atomik `registerFailure`, `forward-headers-strategy: native`; testlar: `ipRateLimit_blocksBeforeAccountLockout`, `failedAttemptCounter_isAtomicUnderConcurrency`, `accountLockout_afterTenFailures_responseDoesNotRevealPassword`.
- [x] **M-4:** parol o'zgarganda boshqa barcha sessiyalar bekor; joriy parolni tekshirishda ham urinishlar hisobga olinsin. — `revokeAllExcept`, joriy parol xatosi → atomik hisoblagich, lockoutda barcha sessiyalar bekor; test `passwordChange_revokesOtherSessions_andCountsWrongCurrentPassword`.
- [x] **M-5:** vaqtinchalik parol bilan sessiya serverda cheklangan (faqat `/auth/me`, `/auth/password`, `/auth/logout`) — almashtirmaguncha boshqa API 403 `PASSWORD_CHANGE_REQUIRED`; UI shunga mos. — `@AllowedWithTempPassword` + interceptor, `/auth/me` da `mustChangePassword`, UI faqat Profilni ko'rsatadi; test `tempPasswordSession_isRestrictedUntilChanged`.
- [x] **M-6:** auditga xavfsizlik hodisalari (ADMIN_LOGIN_FAILED, ADMIN_LOCKED) va manba IP. — V4 `audit_events.source_ip` (so'rovdan avtomatik), `ADMIN_LOGIN_FAILED` (reason=…), UI audit jadvalida IP; test `failedLoginsAndLockout_areAudited_withSourceIp`.
- [x] **M-7:** break-glass: `ADMIN_RECOVERY_EMAIL` + `ADMIN_RECOVERY_PASSWORD` env berilsa va shu email'li admin bloklangan/2FA yo'qotilgan bo'lsa — startupda qayta tiklash (audit bilan), hujjatlash. — `recoverFromEnvironment` (V5 `admin_recovery_log`: bir parol bir marta), OPERATIONS §4C; test `AdminRecoveryIT`.
- [x] **M-8:** `docs/OPERATIONS.md` §4B: public HTTPS varianti uchun IP allowlist majburiy deb yozish, Caddy misoliga `remote_ip` cheklovi. — §4B qayta yozildi (`@allowed remote_ip`, aks holda 403; IP doimiy bo'lmasa SSH/VPN).
- [x] **LOW (admin):** HSTS sarlavhasi; parol uzunligi yuqori chegarasi (72 bayt, BCrypt); `/sellers` paging overflow → 400; SUPPORT uchun grant so'rovlariga limit (masalan, 1 sotuvchiga 24 soatda 3 ta). — HSTS (faqat HTTPS), `PASSWORD_TOO_LONG`, `PAGE_RANGE` (sellers + audit), `GRANT_LIMIT`; test `adminHardening_hstsPasswordLengthPagingAndGrantLimit`.

## 2-bosqich — Backup va ishonchlilik
- [x] **H-2 / M-17:** `backup-loop.sh`: rotatsiya faqat muvaffaqiyatli backupdan keyin, kamida oxirgi 7 nusxa doim saqlanadi; holat fayli (`/backups/status.json`: lastSuccessAt, lastError) yoziladi; xatoda Telegram alert (`ALERT_TELEGRAM_CHAT_ID`, bot tokeni orqali) — ixtiyoriy env. — rotatsiya faqat muvaffaqiyatdan keyin, MIN_KEEP=7, `status.json`, fayl 600; test `scripts/tests/backup-loop.test.sh` (CI). Telegram alert postgres image'da curl yo'qligi sababli ilovada (keyingi band) qilinadi.
- [x] **H-2 (davomi):** ilova `status.json` ni o'qiydi → admin `/health` va `/actuator/health` da backup holati; compose'da backup healthcheck (oxirgi muvaffaqiyat < 26 soat). — `ops/BackupMonitor` (HealthIndicator "backup", admin `/health.backup`, `ALERT_TELEGRAM_CHAT_ID` alert), compose backup healthcheck + app `/backups:ro`; test `BackupMonitorTest`.
- [x] **M-15 / M-16:** backupni shifrlash (`BACKUP_ENCRYPTION_PASSPHRASE` berilsa `gpg --symmetric` yoki `age`), fayl huquqlari 600; off-site nusxa uchun `rclone` misoli (`docs/OPERATIONS.md`). — gpg AES256 (`BACKUP_ENCRYPTION_PASSPHRASE`, fd orqali), gpg yo'q bo'lsa xato; fayllar 600; OPERATIONS §5 rclone misoli; test backup-loop.test.sh (4-holat).
- [x] **M-18:** `restore.sh`: tiklashdan oldin joriy bazaning xavfsizlik snapshoti; `--single-transaction`; xatoda to'xtash. — oldindan `pg_restore --list` tekshiruvi, `backups/pre-restore/` snapshot, `--single-transaction --exit-on-error`, `.gpg` qo'llab-quvvatlash (restore-drill ham); test `scripts/tests/restore.test.sh` (docker shim, CI).
- [x] **M-12:** bot holatini aks ettiruvchi `HealthIndicator` (poller oxirgi muvaffaqiyati < 120 s); `app` uchun Docker healthcheck. — `BotHealthIndicator` ("bot"), health group `container` (db+bot), compose app healthcheck (bash /dev/tcp, curl kerak emas); testlar `BotHealthIndicatorTest`, `containerHealthGroup_isExposed`.
- [x] **M-20:** compose'da xotira limitlari (`mem_limit`), JVM sozlamasi bilan mos; README/OPERATIONS RAM talablari bir xil. — db 512m / app 768m (MaxRAMPercentage=75) / backup 256m; README va OPERATIONS §1: 2 GB RAM.
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

## 6½-bosqich — Yangi Superadmin dizaynini kodga ko'chirish
Reja va qarorlar: `design/superadmin/INTEGRATION.md`. Manba: `design/superadmin/` (Claude Design eksporti, 2026-11-07).
Audit bandlari (1–6-bosqich) tugagach boshlanadi.
- [ ] **D-1:** tokenlar, components.css, self-host shriftlar (`@fontsource`), qorong'i mavzu.
- [ ] **D-2:** `design/superadmin/components/**` primitivlarini `admin-ui/src/components/` ga TSX qilib ko'chirish; `format.ts`.
- [ ] **D-3:** App shell — Sidebar, Topbar, CommandPalette (⌘K), ToastStack, ConfirmDialog (sabab majburiy → API).
- [ ] **D-4:** Auth ekranlari — login, 2FA stepper, OTP, lockout, majburiy parol almashtirish, sessiya tugashi.
- [ ] **D-5:** dizayn uchun backend endpointlari (INTEGRATION.md 5-band) — har biriga test.
- [ ] **D-6:** sahifalar — Dashboard, Tizim holati, Sotuvchilar (+drawer), Sotuvchi kartasi (PrivacyGuard → so'rov → ReadOnly → re-lock), Kirish so'rovlari, Qo'llab-quvvatlash, Audit, Administratorlar, Profil.
- [ ] **D-7:** holatlar (skeleton/bo'sh/xato/403), mobil va planshet, qorong'i, fokus tartibi, Playwright skrinshot-tekshiruvi; eski UI kodi olib tashlanadi.

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
| 2026-11-07 11:10 | Superadmin dizayni repoga qo'shildi (`design/superadmin/`), 6½-bosqich rejaga kiritildi | — | Dizayn audit tugagach ko'chiriladi |
