# Audit tuzatishlari — yakuniy hisobot

Manbalar:
- `PRODUCTION_AUDIT_2026-11-06.md` (1 CRITICAL, 3 HIGH, 26 MEDIUM, 83 LOW) — 2026-11-07 da yopildi (pastdagi 1–6 bo'limlar);
- `PRODUCTION_REAUDIT_2026-11-07.md` (N-1…N-12 va ~75 LOW) va `PRODUCTION_REAUDIT_2026-11-13.md` (yangi kod LOW) —
  2026-11-13 da yopildi (**«8-bosqich» bo'limi**).

> Oldingi versiyadagi «barcha MEDIUM yopildi» degan gap faqat 06-noyabr auditiga tegishli edi (13-noyabr auditi buni
> to'g'ri ta'kidlagan). Endi 07- va 13-noyabr topilmalari ham shu hisobotda.
Batafsil ish rejasi va har band izohi: `docs/AUDIT_PROGRESS.md`.

## Natija

| Ko'rsatkich | Holat |
|---|---|
| `mvn -B verify` | **164 test, 0 xato** (2026-11-13) |
| Admin UI | `npm run build` va `npm run lint` toza, `npm audit --omit=dev` — 0 zaiflik |
| Skript testlari | `restore.test.sh` (7 holat), `backup-loop.test.sh` — o'tdi; shellcheck toza |
| UI vizual tekshiruvi | `admin-ui/e2e/screens.py`: 2 mavzu × 3 kenglik × 8 sahifa, SUPERADMIN va SUPPORT bilan — 0 muammo |
| CI | gitleaks (sha256), osv-scanner (Java + npm), shellcheck, Docker image build, UI lint/audit; action'lar SHA bilan; Dependabot |

**Kod tomonidan tuzatilishi mumkin bo'lgan barcha CRITICAL, HIGH va MEDIUM topilmalar yopildi.** Ochiq qolganlari faqat loyiha egasi bajara oladigan ishlar (pastda).

## CRITICAL va HIGH

| Topilma | Nima qilindi | Commit | Test |
|---|---|---|---|
| C-1 — sirlar `.env.example` da | Shablon tozalandi; gitleaks (CI + pre-commit); `.env.example` bo'shligi CI'da tekshiriladi | 292cd7b, 9227471 | `scripts/check-env-example.sh`, CI `secrets` |
| H-1 — admin auth'ni URL orqali chetlab o'tish | Spring MVC interceptor + `RequestFirewall`, deny-by-default, `@PublicAdminEndpoint` | 4b32e48 | `AdminApiIT.authBypassViaPathTricks_isRejected` (17 yo'l) |
| H-2 — backup xatolari jim | Rotatsiya faqat muvaffaqiyatdan keyin, min 7 nusxa, `status.json`, admin `/health` va actuator'da holat, Telegram alert, compose healthcheck | e3e79cd, 78bf6f4 | `scripts/tests/backup-loop.test.sh`, `BackupMonitorTest` |
| H-3 — huquqiy shartlar | **Egasi bajaradi** (yurist kerak). Texnik tomoni tayyor: shartlar versiyasi matn xeshiga bog'langan (`TermsGuard`, V6) | 2e9840e | `termsVersion_isBoundToTextHash` |

## MEDIUM

| Topilmalar | Commit |
|---|---|
| M-1 TOTP replay · M-2/M-3 IP rate-limit, atomik hisoblagich · M-4 parol almashganda sessiyalar · M-5 vaqtinchalik parol cheklovi · M-6 xavfsizlik auditi + IP · M-7/M-8 break-glass, public HTTPS allowlist | 3e2f9e4, 19df16b, 64b8291, 38a7799, 8b0352f, 06f42b8 |
| M-9 parallel qayta ishlash · M-10 at-least-once · M-11 Telegram 429/5xx retry · M-12 bot healthcheck · M-13 Prometheus · M-14 Unicode/4096 limit | 3a4101d, 0279eba, c6d9e53, a371a46, f065e90, c353864 |
| M-15/M-16 backup shifrlash, off-site · M-17 (H-2 bilan) · M-18 xavfsiz restore · M-19 Spring Boot 3.5.16 · M-20 xotira limitlari | 2d1e5f1, 60bda5f, cd783b0, 0d7a6eb |
| M-21/M-22 admin va poller testlari | 2b2053b, 9bfd413 |
| M-23…M-26 o'chirish matni, qayta ochish, botda bekor qilish, onboardingda o'chirish | ce26c7f, dfe4f7e |

Har bandning testi `docs/AUDIT_PROGRESS.md` da ko'rsatilgan.

## LOW (guruhlar bo'yicha)

Admin, DevOps, maxfiylik, hisob (engine 1.0.0 → 1.0.1, `DECISIONS.md`), DB, testlar, CI va UI guruhlari bajarildi — commitlar 3ae46b9, 21a8f87, 2e9840e, 7a11149, eabe7bc, 2c13a04, b5f62fe, 5d398f1.

## Yangi Superadmin dizayni (6½-bosqich)

Claude Design eksporti (`design/superadmin/`) kodga to'liq ko'chirildi: tokenlar va komponentlar (D-1, D-2), qobiq (D-3), kirish ekranlari (D-4).

**Backend endpointlari (D-5).** Har biri testlangan:
- `/dashboard` (KPI, seriyalar, voronka, retention);
- `/notifications`;
- sotuvchi kartasi;
- grants filtrlari va sahifalash;
- sessiyalar;
- health DB latency;
- support meta.

**Barcha sahifalar (D-6):**
- Dashboard va Tizim holati;
- Sotuvchilar va Sotuvchi kartasi (rozilik → faqat o'qish → avtomatik qayta yopilish);
- Kirish so'rovlari;
- Qo'llab-quvvatlash, Audit, Administratorlar va Profil.

**Holatlar va moslashuv (D-7):**
- skeleton, bo'sh holat, xato va 403 holatlari;
- `ErrorBoundary`;
- fokus tartibi;
- qorong'i mavzu, planshet va mobil ko'rinish.

Tailwind va eski UI kodi olib tashlandi. Commitlar: b46147d … c4b134b.

Yangi migratsiya: **V8** (`users.last_seen_at`, `user_activity_days`). Unda faqat sana saqlanadi, tijoriy ma'lumot yo'q.

## 8-bosqich — qayta auditlar (2026-11-13)

| Topilma | Nima qilindi | Commit | Test |
|---|---|---|---|
| C-1 (REGRESSED) | Hisobotlardan sir prefikslari va sir yashiringan commit ID olib tashlandi. Parollarni almashtirish — **egasi** | 84eb90f | — |
| N-1 | Shartlar xeshi CRLF/CR/BOM'ga bog'liq emas; `.gitattributes` `eol=lf` | 84eb90f | `TermsGuardTest` |
| N-2/N-3 | `status.json` yo'q → MISSING (health DOWN + alert); alert sozlanmasa WARN | a64a16d | `BackupMonitorTest` |
| N-4/N-5 | restore-drill nullglob; restore vaqtinchalik bazaga → Flyway tekshiruvi → bazalarni almashtirish, `--rollback` | a64a16d | `restore.test.sh` |
| M-15/16 | Backup shifrlash majburiy; snapshot'lar shifrlanadi va rotatsiya qilinadi | a64a16d | `backup-loop.test.sh` |
| N-6, M-5 qoldig'i, auth LOW | Atomik IP limit, `PASSWORD_REUSED`, step-up parol, enumeration yo'q, 409/413, confirmTotp 400/lockout | 9367f9a | `ipRateLimit_isAtomicUnderConcurrency`, `authEdgeCases_*`, `privilegedActions_*` |
| N-7/N-12, Telegram LOW | Doimiy worker navbatlari, dead-letter, watchdog, polling advisory lock, dublikatsiz sendMessage, HTML-xavfsiz bo'lish, OOM'da exit | f5c5f67 | `TelegramPollerTest`, `PollingLockIT`, `TextUtilTest` |
| N-9/N-10/N-11, maxfiylik LOW | `AccountPurger`, admin «To'liq o'chirish», onboarding retention, `/delete`, grantlar bekor, foydalanuvchiga xabar | a289971 | AdminApiIT (7 ta) |
| SUPPORT/CSV LOW | Eksport faqat SUPERADMIN, karta ma'lumoti va audit, CSV injection, vaqt offseti, kesish ogohlantirishi, indekslar | b153930 | `auditFilters_andCsvExports` |
| N-8, DevOps LOW | osv-scanner, Dependabot, pinlangan action'lar, CI huquqlari, yangilash tartibi | ec87444 | CI |
| 13-noyabr yangi kod LOW | Dashboard so'rovlari, grants server sahifalash, viewer tez yopiladi, ildiz ErrorBoundary, lazy UI | 3284a38 | `grants_filtersAndDataPagination`, Playwright |
| Qolgan LOW | Botda faol ruxsatlar, o'chirish nonce, guruh rate-limit, grant limit lock, ishonchli proxy | b6498fd | `deleteFinalButton_requiresNonce`, `sellerSeesActiveGrantsInBot_*` |

Yangi migratsiyalar: **V9** (`purged_at`, `status_before_deletion`), **V10** (indekslar). Qarorlar: `docs/DECISIONS.md` → «8-bosqich».

**Lokal ishga tushirishga ta'sir:** backup shifrlash endi majburiy. Lokal sinovda `.env` ga `BACKUP_ENCRYPTION_PASSPHRASE`
bering yoki ataylab `BACKUP_ALLOW_PLAINTEXT=true` qo'ying — aks holda admin panel backup xatosini ko'rsatadi.

## Qoldirilganlar (sababi bilan)

| Topilma | Sabab / keyingi qadam |
|---|---|
| TOTP secret DB'da ochiq matnda | Backup shifrlangan. DB darajasida shifrlash uchun kalitni boshqarish (KMS yoki alohida env kalit + rotatsiya) qarori kerak. |
| Versiyalangan image, registry va rollback avtomatlashtirilmagan | Registry (masalan, GHCR) va deploy jarayoni egasining qarori. Hozircha rollback qo'lda: `git checkout <tag>` + `restore.sh --rollback` (OPERATIONS §5). |
| Base image'lar digest bilan pinlanmagan | Dependabot (docker) haftalik yangilaydi. Digest pinlash registry'ga tarmoq ruxsati bilan qilinadi. |
| Sirlar `docker inspect` da ko'rinadi | Docker secrets / `_FILE` o'zgaruvchilariga o'tish kerak. Server root huquqiga ega odamgagina ko'rinadi. |
| Lokal PG 14 va prod PG 16 | Compose va CI PG 16 ishlatadi; lokal muhit dasturchiga bog'liq. |
| UI: OtpInput siljishi, sidebar va ⌘K fokus nazorati, 2FA sozlashdan orqaga qaytish, sessiya tugashidan oldin ogohlantirish | Funksional xavf yo'q, launchni to'smaydi. Keyingi UI iteratsiyasida. |
| `version` ustunini native UPDATE'lar oshirmaydi | Ziddiyatli yozuvlar hozircha bitta joydan (admin) bo'ladi va 409 qaytadi. Kerak bo'lsa barcha native UPDATE'lar `version = version + 1` ga o'tkaziladi. |
| Spring Boot 4.x | Alohida katta migratsiya (Jackson 3, Spring 7; 2–5 kun). 3.5.x oxirgi patch o'rnatilgan, CVE skaner CI'da. |

## Loyiha egasi bajaradigan ishlar
1. **DB va admin parollarini almashtirish** — ular ochiq repo tarixida ko'ringan. Bot tokeni allaqachon almashtirilgan.
   Repo'ni private qilish yoki tarixni tozalash (force push) — faqat egasining qarori bilan.
2. **H-3** — yurist tasdiqlagan xizmat shartlari va maxfiylik siyosati. Matn o'zgarsa, `TERMS_VERSION` ham yangilanadi.
   Matnda real saqlash muddatlari bo'lsin: audit 365 kun, backup'larda o'chirilgan ma'lumot 14 kungacha,
   ro'yxatdan o'tmagan yozuvlar 30 kun, `DELETION_PURGE_DATA` qarori.
3. Ikki savolga javob berish:
   - `LIMIT_APPLIED` limitlari nimani sanaydi;
   - Spring Boot 4.x ga o'tish qachon rejalashtiriladi.
4. Production'da `.env` ga `BACKUP_ENCRYPTION_PASSPHRASE` va `ALERT_TELEGRAM_CHAT_ID` ni to'ldirish.
5. Audit hisobotlarini ommaviy repoga commit qilmaslik (yoki repo'ni private qilish).
