# Audit tuzatishlari — yakuniy hisobot

Manba: `PRODUCTION_AUDIT_2026-11-06.md` (1 CRITICAL, 3 HIGH, 26 MEDIUM, 83 LOW).
Bajarilgan ish: 2026-11-07 (avtomatik sessiya 11:30–13:15 va kechki davom ettirish).
Batafsil ish rejasi va har band izohi: `docs/AUDIT_PROGRESS.md`.

## Natija

| Ko'rsatkich | Holat |
|---|---|
| `mvn -B verify` | **145 test, 0 xato** (2026-11-07 23:23) |
| Admin UI | `npm run build` va `npm run lint` toza, `npm audit --omit=dev` — 0 zaiflik |
| UI vizual tekshiruvi | `admin-ui/e2e/screens.py`: 2 mavzu × 3 kenglik × 8 sahifa, SUPERADMIN va SUPPORT bilan — 0 muammo |
| CI | gitleaks, shellcheck, Docker image build, UI lint/audit, bog'liqliklar hisoboti |

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

## Qoldirilganlar (sababi bilan)

| Topilma | Sabab / keyingi qadam |
|---|---|
| TOTP secret DB va backupda ochiq matnda (LOW) | Backup `BACKUP_ENCRYPTION_PASSPHRASE` bilan shifrlanadi. DB darajasida shifrlash kalitni boshqarish (KMS yoki env kalit, rotatsiya) qarorini talab qiladi, shu sabab V1.1 ga qoldirildi. |
| Ikkinchi bot instansiyasi texnik bloklanmagan (LOW) | Telegram `409` holati health'da DOWN bo'lib ko'rinadi. Bitta server, bitta compose — amalda yetarli. Ko'p serverga o'tilganda DB advisory lock qo'shiladi. |
| Production hostda build, versiyalangan image va rollback yo'q (LOW) | Registry (GHCR) va deploy jarayoni egasining qarori. CI image'ni build qiladi, lekin push qilmaydi. |
| Lokal PG 14 va prod PG 16 farqi (LOW) | Compose va CI PG 16 ishlatadi. Lokal muhit dasturchining mashinasiga bog'liq. |
| Spring Boot 4.x | Alohida katta migratsiya (Jackson 3, Spring 7). Hozir 3.5.16 — oxirgi patch. Egasiga savol sifatida qoldirildi. |

## Loyiha egasi bajaradigan ishlar
1. **DB va admin parollarini almashtirish** — ular ochiq repo tarixida ko'ringan. Bot tokeni allaqachon almashtirilgan.
2. **H-3** — yurist tasdiqlagan xizmat shartlari va maxfiylik siyosati. Matn o'zgarsa, `TERMS_VERSION` ham yangilanadi.
3. Ikki savolga javob berish:
   - `LIMIT_APPLIED` limitlari nimani sanaydi;
   - Spring Boot 4.x ga o'tish qachon rejalashtiriladi.
4. Ixtiyoriy: git tarixidagi eski sirlarni tozalash (force push kerak bo'ladi — faqat egasining roziligi bilan).
