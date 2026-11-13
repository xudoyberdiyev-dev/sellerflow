# SellerFlow: Uchinchi audit (2026-11-13)

| | |
|---|---|
| **Obyekt** | `main` @ `7371967` (oldingi audit `b2a8b34`, 2026-11-07). Oraliqda 6 ta commit: admin UI qayta qurilgan (D-5…D-7), dashboard, V8 migratsiyasi, `docs/AUDIT_FIX_REPORT.md` |
| **Usul** | Faqat o'qish. 07-noyabr hisobotidagi har bir topilma kod bo'yicha qayta tekshirildi. Yangi kod ko'rib chiqildi, adversarial tekshiruv o'tkazildi, build va testlar nusxada bajarildi. Jami 27 agent |
| **Maxfiylik** | Bu hisobotda sirlar, ularning prefikslari va sir yashiringan commit ko'rsatilmagan |

---

## 1. Qisqa xulosa

**Hukm:** ✅ yopiq beta uchun tayyor. ❌ public production uchun hali tayyor emas.

**Baho:** 8.0 / 10, 07-noyabrdagi bilan bir xil.

**Asosiy sabab:** 07-noyabrdan beri ish deyarli faqat admin panel dizayniga ketgan. 07-noyabr qayta auditida topilgan backend, ops va maxfiylik muammolari **deyarli tuzatilmagan**. `docs/AUDIT_FIX_REPORT.md` faqat 06-noyabr auditini qamrab oladi. Undagi "barcha MEDIUM yopildi" degan gap 07-noyabrdagi yangi topilmalarga (N-1…N-12) tegishli emas.

### 07-noyabr topilmalarining holati (78 band)

| Holat | HIGH + MEDIUM (22) | LOW (56) | Jami |
|---|---|---|---|
| ✅ FIXED | 1 | 5 | **6** (8%) |
| 🟨 PARTIAL | 5 | 2 | **7** (9%) |
| ❌ NOT_FIXED | 14 | 48 | **62** (79%) |
| ⛔ REGRESSED | 1 | 1 | **2** |
| 👤 Faqat egasi bajara oladi | 1 | 0 | **1** |

### Umumiy yo'l (06-noyabrdan beri)

| Audit | CRITICAL | HIGH | MEDIUM | Testlar |
|---|---|---|---|---|
| 06-noyabr | 1 | 3 | 31 | 79 |
| 07-noyabr | 0 | 2 | 12 | 142 |
| **13-noyabr** | **0** | **2** | **12** (yangi MEDIUM yo'q) | **145 ✅** |

Birinchi auditdagi 78 topilmadan 47 tasi 07-noyabrdayoq tuzatilgan edi. Hozir ochiq qolganlari asosan ikkinchi auditda topilgan chekka holatlar.

---

## 2. ✅ Nima bartaraf qilindi (07-noyabrdan beri)

| Band | Natija |
|---|---|
| Yangi commitlarda sir sizib chiqishi | Yo'q. 6 commit tekshirildi, gitleaks CI'da ishlaydi |
| Dark mode buzilgan edi | ✅ |
| Grant bekor qilish `window.confirm` bilan, double-click | ✅ ConfirmDialog va busy holat |
| Tailwind preflight `base.css`ni bekor qilardi | ✅ Tailwind butunlay olib tashlandi |
| D-5 API'lari UI'da ishlatilmagan edi | ✅ |
| Grant ko'rishda faqat 10 ta yozuv | ✅ |
| ErrorBoundary | 🟨 Qo'shilgan, lekin Login va Topbar ustida emas |
| Grant revoke bo'lsa ma'lumot ekranda qoladi | 🟨 Qisman |

---

## 3. ❌ Hali ochiq: public launchgacha majburiy

### HIGH
| ID | Muammo | Kim bajaradi |
|---|---|---|
| C-1 | Sirlar ommaviy GitHub repo tarixida. Hujjatlarga ko'ra DB va superadmin parollari **hali almashtirilmagan**. Bundan tashqari, 07-noyabr hisoboti ommaviy repoga push qilingan va sir qaysi commitda ekanini aniq ko'rsatadi (REGRESSED). 06-noyabr hisobotining 47-qatorida sirlarning 4 belgilik prefikslari hali turibdi | 👤 Egasi: parollarni almashtirsin, repoga private qilish yoki tarixni tozalashni ko'rib chiqsin, hisobotlardagi prefikslarni o'chirsin |
| H-3 | `terms_uz.txt` hali "QORALAMA v0.1", maxfiylik siyosati yo'q | 👤 Egasi va yurist |

### MEDIUM: hech biri tuzatilmagan
| ID | Muammo | Joy | Taxminiy ish hajmi |
|---|---|---|---|
| N-1 | TermsGuard CRLF'ni normallashtirmaydi. Bu mashinada xavf **tasdiqlandi** (`core.autocrlf=true`, `terms_uz.txt` → `w/crlf`): Windows'da build qilingan image ishga tushmaydi | `account/TermsGuard.java:36-41`, `.gitattributes` | 0.5 soat |
| N-2 | `status.json` yo'q bo'lsa, backup health UP | `ops/BackupMonitor.java:60, 81` | 1 soat |
| N-3 | Backup alerti standartda o'chiq, startupda WARN ham chiqmaydi | `BackupMonitor.java:90` | 0.5 soat |
| N-4 | `restore-drill.sh` jimgina exit 2 qiladi, oylik tiklash sinovi ishlamaydi | `scripts/restore-drill.sh:5` | 0.5 soat |
| N-5 | `restore.sh` eski sxemadagi backupni tiklasa, app ishga tushmaydi. Rollback tartibi yo'q | `scripts/restore.sh:41-49` | 3–4 soat |
| N-6 | Login rate-limit TOCTOU (M-2 qoldig'i) | `AdminController.java:106-110`, `LoginRateLimiter.java` | 2 soat |
| N-7 | Poller o'lsa yoki OOM bo'lsa tiklanmaydi. Retry limiti va dead-letter yo'q | `TelegramPoller.java:79-157`, `Dockerfile:26` | 4–6 soat |
| N-8 | Spring Boot 3.5.16 OSS muddati tugagan, CI'da Java CVE skaneri yo'q | `pom.xml:9-10`, `ci.yml:102` | Skaner: 2 soat. Boot 4.x: 2–5 kun |
| N-9 | Qayta ochilgan CLOSED akkauntlarning eski ma'lumoti hech qachon o'chirilmaydi | `DeletionJob.java`, `AccountService.java` | 3 soat |
| N-10 | "To'liq o'chirishni support orqali so'rang" va'dasining mexanizmi yo'q | `bot/Texts.java:268-270` | 2–4 soat |
| N-11 | `users` yozuvi rozilikdan oldin yaratiladi, retention yo'q | `BotDispatcher.java`, `AccountService.java` | 3–4 soat |
| N-12 | TelegramPoller'da partiya darajasidagi to'siq: bitta sekin foydalanuvchi hammani ushlab turadi | `TelegramPoller.java:126-143` | 4–6 soat |
| M-5 qoldig'i | Vaqtinchalik parolni xuddi shu parol bilan "almashtirish" mumkin | `AdminAuthService.java:479-482` | 0.5 soat |
| M-15/16 | Backup shifrlash standartda o'chiq, off-site avtomatlashtirilmagan | `.env.example:37`, compose | 2 soat |

**Texnik MEDIUM'lar jami:** taxminan **4–5 ish kuni**. Bunga Spring Boot 4.x ga o'tish kirmaydi.

### LOW: 48 tasi ochiq (qisqacha)
- **Xavfsizlik va API:** CSV injection `;` yoki tab orqali. SUPPORT butun bazani eksport qila oladi. Sotuvchi kartasi SUPPORT'ga ism va biznes nomini ko'rsatadi. Step-up yo'q. `/auth/totp/confirm` null bo'lsa 500. Optimistic lock xatosi 500 qaytaradi. So'rov tanasi hajmi cheklanmagan.
- **Konkurrentlik:** `BotStatus` atomik emas. Worker'dagi `Error` cheksiz retry qiladi. DeletionJob TOCTOU. Shutdown faqat 10 s kutadi.
- **Telegram:** `sendMessage` qayta yuborilganda dublikat xabar chiqadi. `split` HTML teg ichidan kesishi mumkin.
- **DevOps:** tuning env'lari konteynerga uzatilmaydi. Heap 75%. Snapshot'lar shifrlanmagan. CI action'lari pinlanmagan. Floating base image. Sirlar `docker inspect`da ko'rinadi.
- **Maxfiylik va UX:** admin o'chirish so'rovini jimgina bekor qila oladi. SUSPENDED foydalanuvchi o'chirishni so'ray olmaydi. `/delete` e'lon qilinmagan. O'chirish so'rovi grantlarni bekor qilmaydi.

To'liq ro'yxat: `C:\Users\Lenovo\AppData\Local\Temp\claude\e4ff3b5241f82ab4\2f8e8d73-1dc9-456e-95c1-34fff295e007\scratchpad\reaudit3.json`

---

## 4. 🆕 Yangi kodda topilganlar (hammasi LOW)

| Muammo | Joy |
|---|---|
| Dashboard kunlik seriyasi har kun uchun to'liq jadval skanerlaydi. `created_at` va `saved_at` bo'yicha indeks yo'q, statement timeout yo'q, bot bilan bir xil pool ishlatiladi | `admin/DashboardService.java:44-87` |
| Grants ro'yxati serverda 100 ta qatorga kesiladi. Tab sonlari va filtrlar shu kesilgan to'plam ustida hisoblanadi | `AccessGrantService.java:211-217` |
| V8 faollik ma'lumoti (`user_activity_days`, `last_seen_at`) akkaunt yopilganda yoki purge qilinganda tozalanmaydi, retention yo'q | `DeletionJob.java`, `V8__user_activity.sql` |
| `GET /support` SUPPORT roliga SUPERADMIN email'ini (`updatedBy`) ko'rsatadi | `AdminController.java:466-478` |
| Sotuvchi botda grantni bekor qilsa, ochiq viewer'dagi ma'lumot 60 daqiqagacha ko'rinib turadi | `admin-ui/src/pages/Grants.tsx:92-118` |
| SellerDetail'da "so'rovni bekor qilish" xatoni e'tiborsiz qoldiradi va doim "muvaffaqiyatli" deb ko'rsatadi | `admin-ui/src/pages/SellerDetail.tsx:49-54` |
| Suspend va reactivate sababi faqat UI'da majburiy | `AdminController.java:368-383` |
| **Bundle yana o'sdi: 235 → 303 → 356 kB** (REGRESSED). Lazy-loading yo'q | `admin-ui` |
| Notifications har 60 s indekssiz audit join qiladi | `AdminController.java:203-224` |

---

## 5. Build va testlar (nusxada bajarildi)

| Buyruq | Natija |
|---|---|
| `mvn -B verify` (JDK 21) | ✅ **145 test, 0 xato**, BUILD SUCCESS |
| `npm run build` | ✅ JS 356 kB (gzip 115 kB), CSS 51 kB |
| `npm run lint` | ✅ toza |
| `npm audit --omit=dev` | ✅ 0 |
| `npm audit` | 7 ta moderate (dev, minimatch) |

---

## 6. ⏱ Qachon production'ga tayyor bo'ladi?

Taxmin 1 dasturchining to'liq ish kuniga asoslangan. 07-noyabrdagi tezlikda ishlansa (bir kunda 47 band), muddat qisqarishi mumkin.

| Bosqich | Nima | Kim | Muddat |
|---|---|---|---|
| **0. Darhol** | Superadmin va DB parollarini almashtirish. Repoga private qilish yoki tarixni tozalash. Hisobotlardagi prefikslarni o'chirish. N-1 (CRLF) | Egasi + 0.5 soat kod | **1 kun** |
| **1. Yopiq beta** | 0-bosqichdan keyin **hozirning o'zida mumkin** | — | ✅ |
| **2. Texnik tayyorlik** | N-2…N-12, M-5, M-15/16, CI'ga CVE skaner, restore'ni real muhitda sinash | Dasturchi | **4–5 ish kuni** |
| **3. Huquqiy** | Yurist tasdiqlagan shartlar va privacy policy. Matnni retention'ga moslash | Egasi + yurist | **1–3 hafta** (yuristga bog'liq) |
| **4. Public launch** | 2 va 3-bosqichlar tugagach. Bitta real deploy'da tiklash sinovi va 3–5 kun barqaror monitoring | — | — |

**Xulosa:** Ish 16-noyabrda (dushanba) boshlansa:
- texnik qism taxminan **20–21-noyabr**da tugaydi;
- huquqiy hujjatlar tayyor bo'lsa, public launch **~2026-12-01** atrofida realistik.

Bu muddatni belgilovchi asosiy omil yurist. Kod emas.

LOW'lar launchni to'smaydi. Ularni launchdan keyingi 2–4 haftada yopish mumkin. Spring Boot 4.x ga o'tish ham shu davrga kiradi.

---

## 7. Tavsiya
- Admin dizaynidagi ishni to'xtatib, avval 3-bo'limdagi MEDIUM'larni yoping. Ular kichik va aniq ishlar.
- Audit hisobotlarini ommaviy repoga commit qilmang. Yoki repoga private qiling.
- `docs/AUDIT_PROGRESS.md`ga 07-noyabr (N-1…N-12) va 13-noyabr topilmalarini yangi bosqich sifatida qo'shing, aks holda ular unutiladi.
