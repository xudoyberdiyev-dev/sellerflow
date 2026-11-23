# SellerFlow — loyihaning to'liq holati

**Sana:** 2026-11-23 (production readiness auditidan keyin) · **Repo:** `xudoyberdiyev-dev/sellerflow`, `main` · **Holat:** V3 — yopiq betaga tayyorlanmoqda

> **Qisqacha:**
> - V1, V1+, V2, V3 kodda tayyor: 203 avtomatik test o'tadi, CI'da `test` va `docker` job'lari yashil.
> - Production readiness auditi o'tkazildi: kritik kamchiliklar tuzatildi (TOTP shifrlash, 23 zaiflik, CI, HTTPS, sirlar).
> - **Lekin V3 hali production-ready EMAS.** Real server, real Telegram bot va real Uzum kaliti bilan tekshiruv qilinmagan (NOT VERIFIED).
> - Batafsil: `docs/PRODUCTION_READINESS_AUDIT.md`.

---

## 1. Loyiha nima haqida
SellerFlow — marketplace sotuvchilari (birinchi navbatda Uzum Market) uchun Telegram bot va web admin panel. Sotuvchi har bir mahsulotdan va har bir davrdan haqiqatda qancha foyda qolishini ko'radi: komissiya, logistika, qaytarish, reklama, chegirma, saqlash va jarimalardan keyin. Shuningdek narxni qanday qo'yishni va qaysi mahsulot zarar keltirayotganini biladi.

## 2. Texnologiyalar
| Qism | Texnologiya |
|---|---|
| Backend + bot | Java 21, Spring Boot 3.5.16, PostgreSQL 16, Flyway (V1–V14), Maven |
| Admin panel | React + TypeScript + Vite (`admin-ui/`), `/admin/` |
| Ishga tushirish | Docker Compose (app, db, backup) + ixtiyoriy: `docker-compose.proxy.yml` (HTTPS), `docker-compose.secrets.yml` |
| CI | GitHub Actions: testlar, backup/restore (`postgres:16` ichida), image build, gitleaks, shellcheck, osv-scanner |
| Sozlamalar | faqat `src/main/resources/application.properties` |

## 3. Versiyalar holati
| Versiya | Kod | Real muhitda |
|---|---|---|
| V1 — bot va admin panel | ✅ | ❌ NOT VERIFIED |
| V1+ — qo'shimcha modullar | ✅ | ❌ NOT VERIFIED |
| V2 — Uzum integratsiyasi va import | ✅ | ❌ NOT VERIFIED (real kalit yo'q) |
| V3 — Uzum moliya, davr hisoboti | ✅ | ❌ NOT VERIFIED (real kalit yo'q) |
| Production readiness (2026-11-23) | ✅ audit + tuzatishlar | — |
| V4 / V5 / V6 | ❌ boshlanmagan | — |

## 4. Bajarilgan ishlar (qisqa)
- **V1:**
  - foyda va narx hisobi (engine 1.1.0, `BigDecimal`), mahsulotlar, tarix, sozlamalar, akkauntni o'chirish;
  - admin panel: 2FA, rollar, step-up, rozilik asosida kirish, audit;
  - backup/restore, monitoring.
- **V1+:** chegirma, qaytarish, kanal stavkalari, narx variantlari, minimal marja, CSV eksport.
- **V2:** Uzum ulanishi (shifrlangan kalit), mahsulot sinxroni, Excel/CSV import, tannarxni tez kiritish, foyda yo'qotishlari, «Agar…» simulyatori.
- **V3:** Uzum sotuvlari va xarajatlari, davr hisoboti (sof foyda), yo'ldagi pul, eng foydali va zararli mahsulotlar.
- **Production readiness (2026-11-23):**
  - CI tuzatildi;
  - 23 ta ma'lum zaiflik yopildi (jackson, tomcat, postgresql, log4j-api, commons-io/lang3, minimatch);
  - admin TOTP sirlari shifrlandi (V14), kalit almashsa xavfsiz rad;
  - sirlarni fayldan o'qish (Docker secrets);
  - HTTPS proxy (Caddy), server tayyorlash skripti (ufw, swap, fail2ban);
  - Uzum alert va tanaffus tuzatmasi;
  - tenant izolyatsiyasi va moliya chekka holat testlari;
  - toza bazada migratsiya, ilova va real HTTP 2FA sinovi;
  - shifrlangan backup → restore drill.

## 5. Hali qilinmagan yoki tekshirilmagan

### 5.1 Real muhit (NOT VERIFIED)
- Telegram bot real tokeni bilan.
- Uzum API real kaliti bilan: maydonlar semantikasi, WAF, hisobot summalarini kabinet bilan solishtirish.
- Serverda backup, restore va alert.
- Admin UI mobil va desktop'da qo'lda ko'rib chiqish.

### 5.2 Ochiq kamchiliklar (`docs/PRODUCTION_READINESS_PLAN.md`)
| ID | Daraja | Ish |
|---|---|---|
| C-1…C-5 | CRITICAL | parollarni almashtirish, server deploy, real Telegram va Uzum sinovi, serverda restore va alert |
| H-1 | HIGH | Spring Boot 4 ga o'tish — spring-webmvc'dagi 2 ta CVE (bizga tegishli emas, istisno 2027-02-28 gacha) |
| H-2 | HIGH | real Uzum javoblari asosida moliya maydonlarini aniqlashtirish |
| H-3 | HIGH | backup'ni server tashqarisiga (rclone) |
| H-4 | HIGH | yuridik hujjatlar (yurist) |
| H-5 | HIGH | server: 4 GB RAM / 2 vCPU tavsiya |
| M-1…M-6, L-1…L-4 | MEDIUM / LOW | rejada |

### 5.3 Keyingi versiyalar (TZ)
| Modul | Versiya |
|---|---|
| Wildberries / Ozon, raqobatchi narxlari, Health Score, Growth Simulator, AI maslahatchi | V4 |
| Dead stock, yetkazib beruvchilar, xarid rejasi | V5 |
| To'liq pul oqimi bashorati | qisman (yo'ldagi pul bor) |
| Pullik tariflar va to'lov (Click/Payme) | ❌ (yopiq beta — bepul) |

## 6. Loyiha egasidan kerak
1. **Server** (VPS, tavsiya 4 GB RAM, O'zbekistondagi hosting) va SSH kirish — `PRODUCTION_DEPLOYMENT.md`.
2. **Production bot tokeni** (yangi, @BotFather) — faqat serverdagi `.env` ga yoziladi, chatga emas.
3. **O'z Uzum do'koningiz API kaliti** — faqat botga yuboriladi (sinov uchun).
4. **Alert chat ID** va parol menejeri: `SELLERFLOW_SECRETS_KEY`, `BACKUP_ENCRYPTION_PASSPHRASE` uchun.
5. **Eski parollarni almashtirish** — `PRODUCTION_SECURITY.md` §4.
6. **Bulut hisobi** — backup'ni server tashqarisida saqlash uchun (B2/S3/Drive).
7. **Ixtiyoriy domen** — admin panel uchun HTTPS.
8. **Yurist** — `BETA_TEST_PLAN.md` §11 dagi talablar ro'yxati.
9. **3–5 beta sotuvchi** — `BETA_TEST_PLAN.md` §3.

## 7. Hujjatlar
| Fayl | Mazmuni |
|---|---|
| `docs/PRODUCTION_READINESS_AUDIT.md` | To'liq audit: PASS / PARTIAL / FAIL / NOT VERIFIED |
| `docs/PRODUCTION_READINESS_PLAN.md` | Kamchiliklar, ustuvorlik, reja, server talabi |
| `docs/PRODUCTION_DEPLOYMENT.md` | Serverga joylash, backup, restore, monitoring, yangilash, rollback |
| `docs/PRODUCTION_SECURITY.md` | Xavfsizlik choralari, texnik qarzlar, CVE istisnolari, parol rotatsiyasi, incident response |
| `docs/BETA_TEST_PLAN.md` | 3–5 sotuvchi bilan beta, solishtirish, mezonlar, yuridik talablar |
| `docs/DECISIONS.md`, `docs/OPERATIONS.md` | Qarorlar va ekspluatatsiya |
| `README.md` | Umumiy tavsif |
