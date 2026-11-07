# SellerFlow — qarorlar jurnali (V1.0 Closed Beta)

Manba: `SellerFlow_MASTER_TOLIQ_17_SAVOL_JAVOB_2026-11-05.md` (MASTER v1.1).

## 2026-11-05 — loyiha egasi qarorlari

| ID | Qaror | Holat |
|---|---|---|
| DEV-START | "Documentation First" strategiyasi o'zgartirildi: loyiha egasi V1.0 ni kodlashni boshlashni topshirdi. V1 Scope Freeze rasmiy hujjati hali yo'q — quyidagi jadval uning o'rnida. | Qabul qilindi |
| SCOPE-V1.0 | Telegram bot + backend, 6 ta bo'lim + /help. Web Superadmin keyingi bosqichda; hozircha minimal admin API. | Qabul qilindi |
| CR-RELEASE-01 | V1.0 = **faqat Free, yopiq beta**. Checkout, to'lov va limit hisoblagichi yo'q. Tarif katalogida faqat `FREE`, imkoniyatlar `UNLIMITED` (server konfiguratsiyasi). | Qabul qilindi ("default") |
| CR-ROUND-01 | Narx yaxlitlash qadamlari 1 / 100 / 1 000; **standart 1 000 so'm** (Addendum taklif qilgan 1 o'rniga). | Qabul qilindi ("default") |
| CR-PHONE-01 | Telefon raqami V1.0 da **umuman so'ralmaydi** (onboardingda ham, sozlamalarda ham). | Qabul qilindi ("default") |
| CR-SCOPE-01 | Xarajat profillari va CSV eksport V1.0 ga **kirmaydi**. Xarajatlar kalkulyatorda qo'lda kiritiladi. Legacy `UX-COST` reyestri bilan solishtirish — ochiq. | Qabul qilindi ("default") |
| CR-FIN-01 | Reklama (A) va xizmat/ekvayring to'lovi (E) sotish narxining foizi sifatida ham kiritilishi mumkin. Logistika, qadoqlash va boshqa xarajatlar — faqat summa. | Qabul qilindi ("default" — Addendum PM tavsiyasi) |
| CR-FIN-02 | Boshqa bazadagi soliq (masalan, foydadan) faqat 1 dona uchun summa sifatida kiritiladi; bilinmasa — UNKNOWN. Avtomatik soliq formulasi yo'q. | Qabul qilindi ("default") |
| CR-DRAFT-01 | Hisoblash qoralamasi **oxirgi muvaffaqiyatli kiritishdan** 24 soat saqlanadi. /help, /menu va ko'rish amallari muddatni uzaytirmaydi. | Qabul qilindi ("default") |
| CR-DRAFT-02 | Mahsulot yaratish/tahrirlash formasi ham shu qoidaga bo'ysunadi (24 soat). | Qabul qilindi ("default") |
| CR-LOCALE-01 | Interfeys tili: o'zbekcha, lotin yozuvi. Foydalanuvchi matni Unicode (kirill ham saqlanadi). Matnlar `Texts.java` da bir joyda. | Qabul qilindi ("default") |
| CR-CHAT-01 | Biznes oqimlari faqat private chatda. Guruhda faqat "shaxsiy chatda yozing" yo'riqnomasi. | Qabul qilindi ("default") |
| CR-ADMIN-01 | Kirish muddatini tizim belgilaydi: admin so'rovida 30, 45 yoki 60 daqiqa tanlanadi (standart 30), sotuvchi aniq muddat va sababni ko'rib tasdiqlaydi. Ruxsat faqat o'qish uchun, boshqa adminga o'tkazilmaydi, har bir ko'rish auditga yoziladi. Sotuvchi yoki admin istalgan payt bekor qiladi. Akkaunt cheklansa, ruxsat darhol tugaydi. Javobsiz so'rov 24 soatda eskiradi. | Qabul qilindi ("default") — tunda amalga oshirildi |
| UX-DEC-37/38/39 | Sozlamalar: yagona markaz; maydonlar alohida saqlanadi; kanal o'zgarishi tasdiq bilan; o'chirish so'rovi ikki bosqichli. | Qabul qilindi ("default" — DRAFT A variantlari) |

## 2026-11-05/06 tun — V1 ni yakunlash qarorlari (loyiha egasi "o'zing hal qil" topshirig'i asosida)

| ID | Qaror |
|---|---|
| ADM-AUTH | Admin identifikatsiyasi sotuvchi Telegram ID'sidan alohida: email, parol (BCrypt, kamida 12 belgi) va **majburiy TOTP 2FA**. Sessiya 8 soat; token DB'da faqat SHA-256 xeshi sifatida saqlanadi. 10 ta xato urinishdan keyin akkaunt 15 daqiqa bloklanadi (atomik hisoblagich); bitta IP'dan 15 daqiqada 5 ta xatodan keyin 429 `TOO_MANY_ATTEMPTS` (M-2/M-3, 2026-11-07) — IP limiti lockout chegarasidan past, shuning uchun bitta IP adminni bloklay olmaydi. |
| ADM-ROLES | `SUPERADMIN` — hamma amallar. `SUPPORT` — statistika, tizim holati, sotuvchilar ro'yxati, o'z kirish so'rovlari. Bloklash/tiklash, support kontakti, audit va adminlarni boshqarish — faqat SUPERADMIN. |
| ADM-BOOTSTRAP | Birinchi SUPERADMIN `.env` (ADMIN_BOOTSTRAP_EMAIL/PASSWORD) orqali, faqat bazada birorta ham admin bo'lmaganda yaratiladi. |
| ADM-UI | Panel `/admin/` manzilida, Spring Boot orqali beriladi. Server porti faqat 127.0.0.1 da ochiq; tashqaridan kirish SSH tunnel yoki HTTPS reverse proxy orqali. UI: React + TypeScript + Vite + Tailwind. shadcn/ui CLI ishlatilmadi — uning registriga tarmoq ruxsati yo'q edi, shuning uchun shu uslubdagi komponentlar qo'lda yozildi. |
| RECONSENT | Shartlar versiyasi (`TERMS_VERSION`) o'zgarsa, faol foydalanuvchi keyingi murojaatida yangi shartlarni ko'radi. Aniq roziliksiz biznes amallari yopiq, faqat /help va akkauntni o'chirish so'rovi ochiq. Callback'da aynan ko'rsatilgan versiya tekshiriladi. |
| DELETION-JOB | Har kuni 03:15 (Toshkent): o'chirish so'rovidan 30 kun o'tgan akkauntlar CLOSED holatiga o'tadi. Qoralama, biznes nomi va ism tozalanadi, faol kirish ruxsatlari bekor qilinadi. Mahsulot va tarix `DELETION_PURGE_DATA=true` bo'lgandagina o'chiriladi (yurist qaroriga qadar false). Rozilik va audit yozuvlari dalil sifatida saqlanadi. Kutish davrida tiklash — admin panel → «Qayta faollashtirish». |
| RATE-LIMIT | Bitta foydalanuvchi uchun 10 soniyada 20 ta update. Undan oshsa, bir marta ogohlantirish beriladi va qolganlari tashlanadi. |
| BACKUP | Kunlik `pg_dump -Fc`, 14 kun saqlanadi; restore va restore-drill skriptlari. Backupni server tashqarisiga nusxalash — operator vazifasi. |
| CI | GitHub Actions: UI build + PostgreSQL 16 bilan barcha testlar. |

## Texnik qarorlar (kod darajasida)

- **Pul arifmetikasi:** `BigDecimal`. Pul qiymatlari — 2 kasr xona, HALF_UP. Foizdan hisoblangan summa (masalan, 1,5% × narx) tiyingacha yaxlitlanadi. Marja 2 kasr xonagacha.
- **Foiz** kiritishda "10" = 10%. Ichkarida ulushga (0.10) o'giriladi.
- **Kiritish normalizatsiyasi:** `120000`, `120 000`, `120.000` va `1,250,000` qabul qilinadi. 3 xonali guruhlar ming ajratuvchi deb olinadi; boshqa hollarda vergul o'nlik belgisi (`1,5`). Ko'pi bilan 2 kasr xona. Maksimal qiymat 999 999 999 999.
- **Narxni tekshirish:** yaxlitlangan narx profit engine orqali qayta hisoblanadi. Tiyin yaxlitlash sababli maqsadga yetmasa, narx bir qadam oshiriladi (eng ko'pi 50 marta).
- **Tarix sahifalash:** offset bilan, lekin ro'yxat ochilgan paytdagi eng katta `id` (anchor) bilan cheklanadi. Shu sababli parallel Save sahifalarni siljitmaydi.
- **Eski tugmalar:** wizard tugmalari `revision` va qadamni o'z ichiga oladi. Ulardan biri mos kelmasa — "tugma eskirgan" deyiladi va hech narsa o'zgarmaydi.
- **Idempotentlik:** `processed_updates` (Telegram update_id), `calculation_history.result_id UNIQUE`, `products.idempotency_key UNIQUE`, faollashtirishda `businesses.owner_user_id UNIQUE`.
- **Konkurentlik (M-9, 2026-11-07):** poller bitta, qayta ishlash `TELEGRAM_WORKERS` (standart 4) oqimda: partiya `telegramUserId % workers` bo'yicha bo'linadi — bitta foydalanuvchining update'lari doim bitta oqimda, tartib bilan; offset partiya tugagach suriladi. Telegram HTTP chaqiruvlari DB tranzaksiyasidan tashqarida (`AccessGrantService` xabari `afterCommit` da).
- **SKU:** biznes ichida katta-kichik harfdan qat'i nazar noyob. Arxivlangan mahsulot SKU'si ham band hisoblanadi.
- **"Bitta faol jarayon":** faqat ko'rish bo'limlariga (ro'yxatlar, tariflar, yordam) o'tish qoralamani o'chirmaydi, u to'xtatib qo'yiladi. Yangi kiritish jarayonini boshlashda saqlanmagan ma'lumot bo'lsa, tasdiq so'raladi (UX-DEC-17).
- **Admin API auth (H-1, 2026-11-07):** Spring Security qo'shilmadi (bog'liqlik va konfiguratsiya hajmi). O'rniga: `RequestFirewall` (eng birinchi filtr; `;`, `%2f`/`%5c`/`%2e`/`%25`/`%3b`, `\\`, `//`, `.`/`..` segmentlari → 400) va `AdminAuthInterceptor` (`/admin/api/**` path pattern — Spring MVC handler tanlaydigan yo'l bo'yicha). Deny-by-default: faqat `@PublicAdminEndpoint` (login, TOTP tasdiqlash) sessiyasiz ochiq.
- **Update qayta ishlash semantikasi (M-10, 2026-11-07):** at-least-once. `processed_updates` belgisi update MUVAFFAQIYATLI qayta ishlangandan keyin qo'yiladi. Vaqtinchalik infratuzilma xatosida (`common/Infra.isTransient`: baza uzilishi, SQLState 08xxx/57Pxx) poller offset'ni surmaydi, backoff bilan qayta so'raydi — update yo'qolmaydi; foydalanuvchi ayrim xabarlarni ikki marta olishi mumkin. Dastur xatolari (bug) — eskicha: log, foydalanuvchiga umumiy xato, update belgilanadi. Poller `@PreDestroy` da to'xtaydi (joriy update tugashi ≤ 10 s kutiladi).
- **Akkaunt o'chirish va qayta ochish (M-23/M-24/M-25, 2026-11-07):**
  - O'chirish matni nima o'chadi / nima qoladi ni aniq aytadi va `DELETION_PURGE_DATA` ga qarab o'zgaradi.
  - 30 kunlik kutish davrida foydalanuvchi so'rovni botning o'zida bekor qiladi (`dc:` callback, audit `DELETION_CANCELLED`).
  - `DELETION_PURGE_DATA=true` da yopishda `users.telegram_user_id` **tasodifiy manfiy psevdonimga** almashtiriladi. Audit «hash» deb tavsiya qilgan, lekin Telegram ID maydoni kichik (≈10¹⁰) — hash'ni brute-force bilan qaytarish mumkin, shuning uchun bog'lanmaydigan tasodifiy qiymat tanlandi.
  - CLOSED akkaunt egasi «Yangi akkaunt ochish» tugmasi bilan yangidan boshlaydi: eski yozuv Telegram ID'dan ajratiladi (psevdonim), eski ma'lumot yangi akkauntga o'tmaydi; audit `CLOSED_ACCOUNT_DETACHED`.
- **Shartlar versiyasi ↔ matn (LOW maxfiylik, 2026-11-07):** `TermsGuard` startupda `terms_uz.txt` SHA-256 ni `terms_versions` jadvaliga bog'laydi. Matn o'zgarib `TERMS_VERSION` o'zgarmasa ilova **ishga tushmaydi** (xato xabari versiyani oshirishni aytadi). Versiyani oshirish — barcha foydalanuvchidan qayta rozilik.
- **Bloklash va audit (LOW maxfiylik):** SUSPENDED qilinganda REQUESTED/APPROVED grantlar DB'da `REVOKED (SYSTEM)`. USER audit hodisalarida `actor_id` — biznes egasining ichki user ID'si (Telegram ID emas). `DELETION_PURGE_DATA` keyin yoqilsa, DeletionJob oldin yopilgan akkauntlarni ham tozalaydi (`ACCOUNT_PURGED`).
- **Hisob-kitob tuzatishlari (LOW hisob, 2026-11-07; engine 1.0.0 → 1.0.1):**
  - Tavsiya narxi: yaxlitlangan nomzoddan keyin ko'pi bilan 2 qadam **pastga** ham tekshiriladi — `profit()` dagi foizli xarajat yaxlitlashi tufayli pastroq narx ham maqsadga yetishi mumkin edi (fuzz: masalan, tannarx 525 098, komissiya 32,93%, maqsad 39 897 → endi 842 396, avval 842 397). Tavsiya — maqsadga yetadigan eng kichik qadam. Yangi hisoblashlar `calculation_version = 1.0.1` bilan saqlanadi; eskilari o'zgarmaydi.
  - `RoundingMode.UNNECESSARY` → `HALF_UP` (scale 10): 2 xonadan ortiq kasrli qiymat `ArithmeticException` bermaydi; mavjud kiritishlar uchun natija o'zgarmaydi.
  - Raqam o'qish: minglik guruh `0` bilan boshlanmaydi (`"0,075"` endi 75 emas). Foiz maydonlari (`Money.parsePercent`) minglik ajratgichni umuman qabul qilmaydi; vergul/nuqta — o'nli kasr.
  - `PlanService.isAllowed`: `LIMIT_APPLIED` faqat `used < limit_value` bo'lsa ruxsat; foydalanish soni berilmagan joyda (hisoblashlar) — xavfsiz rad. Mahsulot katalogi faol mahsulotlar soni bilan tekshiriladi.

## Ochiq qolganlar (ishga tushirishdan oldin)

1. **Jonli sinov:** haqiqiy Telegram bot bilan, serverda. Avtomatik testlar va soxta Telegram server bilan sinov o'tgan, lekin haqiqiy Bot API bilan sinalmagan.
2. **Yuridik tekshiruv:** xizmat shartlari (`terms_uz.txt` — QORALAMA v0.1), maxfiylik siyosati, O'RQ-547 / O'RQ-1125, hosting joylashuvi, retention siyosati (`DELETION_PURGE_DATA`).
3. **PRD-06 Billing (V1.1):** pullik tariflar, to'lov provayderi (Click/Payme), obuna.
4. **Asl PRD-01 / 02.01 / 02.02 matnlari:** kodni ular bilan qator-ma-qator solishtirish (traceability).
5. **Legacy `UX-COST` (Xarajat profillari)** reyestri bilan solishtirish.
6. **TOTP sirlari** DB'da ochiq holda saqlanadi (baza himoyasiga tayanadi). Keyingi bosqichda ularni alohida kalit bilan shifrlash tavsiya etiladi.
7. **Tashqi monitoring/alerting:** uptime-monitor va backupni server tashqarisiga nusxalash (`docs/OPERATIONS.md`).
8. **Admin ko'rish oynasi:** rozilik asosidagi kirishda oxirgi 10 ta mahsulot va 10 ta hisoblash ko'rsatiladi. Kerak bo'lsa, sahifalash qo'shiladi.
