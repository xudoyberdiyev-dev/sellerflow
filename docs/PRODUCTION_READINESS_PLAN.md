# SellerFlow V3 — Production readiness rejasi

**Sana:** 2026-11-23 · Audit: `docs/PRODUCTION_READINESS_AUDIT.md`

Darajalar: **CRITICAL** (beta'dan oldin albatta), **HIGH** (beta'dan oldin yoki beta boshida), **MEDIUM** (beta davomida), **LOW** (keyin).

## 1. Shu auditda tuzatilganlar

| ID | Daraja | Ish | Dalil |
|---|---|---|---|
| F-1 | HIGH | CI qizil edi: backup/restore testi `postgres:16` ichiga ko'chirildi (pg_dump 17 → 16 muammosi), gpg tekshiruvi qo'shildi | `.github/workflows/ci.yml`, CI run |
| F-2 | HIGH | 23 ta ma'lum zaiflik yopildi: jackson 2.21.8, tomcat 10.1.62, postgresql 42.7.14, log4j-api 2.25.5, commons-io 2.22.0, commons-lang3 3.22.0, minimatch 11 | `pom.xml`, `admin-ui/package.json`, `npm audit` 0, osv-scanner |
| F-3 | HIGH | Admin TOTP sirlari shifrlandi (V14 + converter + migrator) | `AdminApiIT.totpSecret_isEncryptedAtRest…`, real HTTP login |
| F-4 | HIGH | Shifrlash kaliti almashsa admin login 500 → xavfsiz rad, shifrmatn saqlanadi | `AdminApiIT.wrongSecretsKey…`, lokal sinov |
| F-5 | MEDIUM | Sirlarni fayldan o'qish (`*_FILE`, `/run/secrets`), `docker-compose.secrets.yml`, `TELEGRAM_REQUIRED` fail-fast | `SecretFilesEnvironmentPostProcessorTest` |
| F-6 | MEDIUM | HTTPS reverse proxy (Caddy, faqat `/admin`), `deploy/server-setup.sh` (ufw, swap, fail2ban, unattended-upgrades) | `docker compose config`, shellcheck |
| F-7 | MEDIUM | Uzum: kalitlar bo'yicha tanaffus qulfdan tashqarida (boshqa sotuvchilar kutmaydi) | `UzumClientTest` |
| F-8 | MEDIUM | Uzum kunlik sinxronida tizimli xato yoki WAF bo'lsa — operatorga alert | `UzumService.nightly` |
| F-9 | HIGH | Tenant isolation testlari V2–V3 ma'lumotlari uchun (hisobot, import, tannarx oqimi, Uzum kaliti AAD) | `TenantIsolationIT` |
| F-10 | MEDIUM | Moliya: hisobot izchilligi, nol va juda katta qiymatlar testlari | `TenantIsolationIT.report_isDeterministic…` |

## 2. Ochiq ishlar (ustuvorlik bo'yicha)

### CRITICAL — beta'dan oldin
| ID | Ish | Kim | Nima kerak |
|---|---|---|---|
| C-1 | Eski parollarni almashtirish (DB, admin; shubha bo'lsa bot tokeni) | Egasi | `PRODUCTION_SECURITY.md` §4 |
| C-2 | Server: deploy, `.env`, `SELLERFLOW_SECRETS_KEY`, `BACKUP_ENCRYPTION_PASSPHRASE`, `ALERT_TELEGRAM_CHAT_ID` | Egasi (+ Claude yo'riqnoma bo'yicha) | VPS, SSH kirish |
| C-3 | Real Telegram bot bilan smoke test (`BETA_TEST_PLAN.md` §2) | Egasi + Claude | Bot tokeni (faqat `.env` da) |
| C-4 | Real Uzum kaliti bilan: mahsulotlar, sotuvlar, xarajatlar; hisobot summalarini kabinet bilan solishtirish; WAF tekshiruvi | Egasi + Claude | O'z Uzum do'koningiz kaliti (botga, chatga emas) |
| C-5 | Serverda restore drill va alert sinovi (backup'ni ataylab buzib, xabar kelishini ko'rish) | Egasi | Server |

### HIGH
| ID | Ish | Kim | Muddat / izoh |
|---|---|---|---|
| H-1 | **Spring Boot 4 (Spring Framework 7.0.9+) ga o'tish** — 2 ta Critical CVE'ning ochiq tuzatmasi faqat shu yerda. Jackson 3, Hibernate 7, test infratuzilmasi | Claude | 2–5 kun; istisno muddati **2027-02-28** (`osv-scanner.toml`) |
| H-2 | Real Uzum javoblari asosida moliya maydonlarini aniqlashtirish (`commission` qator jamimi, `PARTIALLY_CANCELLED.amount`, sana formati, INCOME kompensatsiyalar) va contract test (anonimlashtirilgan namuna JSON) | Claude | C-4 dan keyin |
| H-3 | Server tashqarisiga backup (rclone → bulut, kunlik), haftalik tekshiruv | Egasi | `PRODUCTION_DEPLOYMENT.md` §6 |
| H-4 | Yuridik: xizmat shartlari, maxfiylik siyosati, Uzum ma'lumotlarini qayta ishlash, saqlash muddatlari, `DELETION_PURGE_DATA` | Egasi + yurist | `BETA_TEST_PLAN.md` §8 |
| H-5 | Server resursi: beta uchun **4 GB RAM / 2 vCPU / 40 GB SSD** (2 GB — faqat swap bilan, chegarada) | Egasi | §3 |

### MEDIUM
| ID | Ish | Kim |
|---|---|---|
| M-1 | Admin UI'ni mobil va desktop'da qo'lda ko'rib chiqish (loading/xato holatlari) | Egasi + Claude |
| M-2 | Server resurs monitoringi: disk ≥ 80% va RAM alerti (cron + alert chat) — `PRODUCTION_DEPLOYMENT.md` §8 dagi skript | Egasi |
| M-3 | Uzum: 35 kundan eski sotuvlar statusini oylik chuqur qayta o'qish | Claude |
| M-4 | Versiyalangan image (GHCR) va bir buyruqli rollback | Claude + egasi (registry qarori) |
| M-5 | Davr hisobotida qaytarilgan pul alohida qator (Uzum `/v1/return` asosida) | Claude |
| M-6 | GitHub Actions: Node 20 deprecated ogohlantirishlari — action versiyalarini yangilash | Claude |

### LOW
| ID | Ish |
|---|---|
| L-1 | Base image'larni digest bilan pinlash |
| L-2 | `DB_PASSWORD` ni ham Docker secret'ga (Postgres `POSTGRES_PASSWORD_FILE`) |
| L-3 | Admin UI mayda kamchiliklari (OtpInput, fokus, sessiya tugash ogohlantirishi) |
| L-4 | Native UPDATE'larda `version` oshirish (optimistic lock izchilligi) |

## 3. Server talabi bahosi

| Komponent | Limit (compose) | Izoh |
|---|---|---|
| PostgreSQL 16 | 512 MB | 3–5 sotuvchi, ~10⁵ sotuv qatori — yetarli |
| App (JVM) | 768 MB | heap ≈ 500 MB; Uzum sinxroni 5 000 SKU / 40 000 sotuv qatorini xotirada yig'adi |
| Backup | 256 MB | kuniga bir marta |
| Caddy (ixtiyoriy) | 128 MB | |
| OS + Docker | ~400 MB | |
| **Jami** | **≈ 2.1 GB** | 2 GB serverda faqat swap bilan; build (Maven + npm) 2 GB'da juda sekin yoki OOM |

**Tavsiya:**
- **Beta:** 4 GB RAM, 2 vCPU, 40 GB SSD, Ubuntu 24.04. Hosting O'zbekistonda — Uzum WAF xorijiy IP'larni bloklashi mumkin, ma'lumot joylashuvi ham qonun talabiga yaqinroq.
- **50+ sotuvchi:** 8 GB, DB `mem_limit` 1–2 GB, `DB_POOL_SIZE` va `TELEGRAM_WORKERS` ni oshirish, backup'ni alohida diskka.

## 4. Production-ready e'lon qilish sharti
Barcha **CRITICAL** (C-1…C-5) bajarilgan va `PRODUCTION_READINESS_AUDIT.md` §1 dagi NOT VERIFIED mezonlar real muhitda PASS bo'lgach. H-1 beta paytida parallel bajarilishi mumkin: istisno asoslangan, muddati bor.
