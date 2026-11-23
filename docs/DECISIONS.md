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
| ADM-UI | Panel `/admin/` manzilida, Spring Boot orqali beriladi. Server porti faqat 127.0.0.1 da ochiq; tashqaridan kirish SSH tunnel yoki HTTPS reverse proxy orqali. UI: React + TypeScript + Vite. 2026-11-07 dan Tailwind olib tashlandi: Claude Design eksportidagi dizayn tizimi (`design/superadmin/`) tokenlar + `components.css` + TSX primitivlar (`admin-ui/src/components/`) sifatida ko‘chirildi; shriftlar va ikonkalar self-host (CSP `self`). |
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

## 2026-11-13 — qayta auditlar (8-bosqich) qarorlari

| ID | Qaror |
|---|---|
| RET-REOPEN (N-9) | Yopilgan akkaunt egasi «Yangi akkaunt ochish»ni bossa, eski akkauntning tijoriy ma'lumoti (mahsulotlar, hisoblashlar, profil, faollik) **darhol o'chiriladi** va Telegram ID psevdonimlanadi. Sabab: ajratilgan yozuvni hech kim (na sotuvchi, na support) topa olmaydi — yetim ma'lumot saqlashning maqsadi yo'q. Rozilik va audit qoladi. |
| RET-PURGE-ADMIN (N-10) | «To'liq o'chirishni qo'llab-quvvatlash orqali so'rang» — admin panelda SUPERADMIN uchun «To'liq o'chirish» (faqat CLOSED yoki DELETION_REQUESTED, sabab + joriy parol, audit `ACCOUNT_PURGED`). |
| RET-ONBOARDING (N-11) | Ro'yxatdan o'tishni tugatmagan (biznes yaratilmagan) yozuv `ONBOARDING_RETENTION_DAYS` (30) kun faolliksiz bo'lsa psevdonimlanadi va yopiladi (kunlik DeletionJob). |
| DEL-SUSPENDED | Bloklangan foydalanuvchi ham `/delete` bilan o'chirishni so'ray oladi; bekor qilsa — bloklangan holiga qaytadi (`status_before_deletion`). O'chirish so'ralganda admin kirish ruxsatlari darhol bekor qilinadi. Admin so'rovni bekor qilsa — foydalanuvchiga sababi bilan xabar boradi. |
| ACT-RETENTION | V8 faollik izlari (`user_activity_days`, `last_seen_at`) akkaunt yopilganda o'chiriladi. |
| BACKUP-ENC | Backup shifrlash standartda majburiy; ochiq backup faqat `BACKUP_ALLOW_PLAINTEXT=true` bilan (lokal sinov). |
| RESTORE-SWAP (N-5) | Tiklash vaqtinchalik bazaga, so'ng bazalar almashtiriladi; eski baza `sellerflow_prev` (`--rollback`). |
| AUTH-STEPUP | Admin yaratish, boshqa adminning 2FA'sini reset qilish va to'liq o'chirish joriy parol bilan tasdiqlanadi. Bloklangan akkaunt login javobi `INVALID_CREDENTIALS` (email mavjudligi oshkor qilinmaydi). |
| POLL-QUEUES (N-12) | Poller har worker'ga doimiy navbat bilan ishlaydi; offset faqat eng kichik tugamagan update'gacha suriladi (at-least-once saqlanadi). Vaqtinchalik xato 15 daqiqa qayta urinish, so'ng dead-letter (log). Bir vaqtda faqat bitta nusxa polling qiladi (PostgreSQL advisory lock). |

## 2026-11-23 — V1+ (Qo'shimcha modullar TZ) qarorlari

| ID | Qaror |
|---|---|
| ENG-1.1.0 | Hisob engine versiyasi **1.1.0**. Saqlangan eski natijalar o'zgarmaydi (snapshot + `calculation_version`). |
| EXP-RETURNS | Yangi xarajat toifasi «Qaytarish xarajati» (`R`): 1 dona uchun summa yoki narxdan %. Oddiy xarajat kabi jamiga qo'shiladi. |
| EXP-DISCOUNT | «Chegirma» (`D`) xarajat emas — **tushumni kamaytiradi**. Samarali narx E = P − chegirma. Chegirma foizi sotish narxi P dan. Boshqa foizli xarajatlar (komissiya, soliq va h.k.) va marja **E dan** hisoblanadi. Chegirma ≥ P bo'lsa natija yo'q (bot xabar beradi). |
| PRICE-DISCOUNT | Teskari narx: formula avval samarali narxni topadi, so'ng P = (E + chegirma summasi) / (1 − chegirma ulushi); keyin yaxlitlash va tekshiruv. Chegirma ulushi ≥ 100% → `NO_SOLUTION`. |
| NOTSET-RD | Kiritilmagan qaytarish/chegirma boshqa toifalar kabi «taxminiy» natija beradi; «Qolganlari yo'q (0)» ularni ham 0 qiladi. 1.1.0 dan oldingi tarix yozuvidan tiklashda R va D = 0. |
| CH-RATES | Har sotuv kanali uchun xarajat stavkalari (`business_channels`, V11) — natija ekranidagi «📌 Stavkalarni … uchun eslab qolish» tugmasi bilan saqlanadi. Chegirma saqlanmaydi (u aksiya, kanal stavkasi emas). Hisob boshida tanlangan kanal stavkalari faqat **kiritilmagan** toifalarga qo'llanadi va komissiya/logistika/qo'shimcha qadamlari o'tkazib yuboriladi (tekshirish sahifasida tahrirlash mumkin). Tekshirish sahifasida kanal almashtirilsa, yangi kanal stavkalari ustidan yoziladi. Sozlamalar → «📌 Kanal stavkalari»: ro'yxat va o'chirish. |
| MIN-MARGIN | Sozlamalar → «📉 Minimal marja» (0–99,99%, `businesses.min_margin_pct`). Natija (foyda yoki tavsiya narxidagi) marjasi shundan past bo'lsa, xabar boshida ogohlantirish. Hisobni bloklamaydi. |
| PRICE-VARIANTS | Natija ekranida «📊 Narx variantlari»: zararsizlik va 10/20/30/40% (+ minimal) marja uchun tavsiya narxlari, joriy yaxlitlash qadamida. Saqlanmaydi (ma'lumot uchun). |
| EXPORT-CSV | Sozlamalar → «📤 Ma'lumotlarni yuklab olish»: `mahsulotlar.csv` va `hisoblashlar.csv` (UTF-8 BOM, formula-injection himoyasi, har biri ≤ 10 000 qator), 10 daqiqada bir marta; audit `USER_DATA_EXPORTED`. |
| PURGE-V11 | To'liq o'chirishda kanal stavkalari va minimal marja ham o'chiriladi. |

## 2026-11-23 — V2 (Uzum integratsiyasi va tahlil) qarorlari

| ID | Qaror |
|---|---|
| UZ-API | Uzum Seller OpenAPI (`api-seller.uzum.uz/api/seller-openapi`), **faqat o'qish**: `GET /v1/shops`, `GET /v1/product/shop/{shopId}` (size=100). Kalit `Authorization` sarlavhasida «Bearer»siz. Bot narx, qoldiq yoki buyurtmalarni o'zgartirmaydi. Javob maydonlari ochiq qo'llanma (uzum-seller-api-guide) va rasmiy swagger asosida; tolerant parsing — noma'lum maydonlar e'tiborsiz. |
| UZ-TOKEN | Sotuvchi kalitini botga yuboradi → bot xabarni **darhol o'chiradi**, kalitni sessiyaga yozmaydi, `/v1/shops` bilan tekshiradi va AES-256-GCM bilan shifrlab `marketplace_connections` ga yozadi (AAD = biznes ID). Shifrlash kaliti `SELLERFLOW_SECRETS_KEY` faqat muhitda — DB va backup'da yo'q. Kalit yo'q bo'lsa Uzum funksiyasi o'chiq. Ekranda faqat oxirgi 4 belgi. Uzish — kalit darhol o'chiriladi; akkaunt yopilganda ham. |
| UZ-LIMITS | ~2 so'rov/s (har kalitga 550 ms tanaffus), kunlik 100 000. Kunlik 429 (`x-ratelimit-remaining-per-day: 0`) — qayta urinilmaydi, holat `QUOTA`. Sekundlik 429 va 5xx — cheklangan qayta urinish. 403 HTML — WAF (server IP), kalit «buzilgan» deb belgilanmaydi. 401/403 JSON — `INVALID`, fon sinxroni to'xtaydi. |
| UZ-SYNC | Har SKU — bitta mahsulot (`external_sku_id` bo'yicha upsert; avval qo'lda qo'shilgan bo'lsa — SKU bo'yicha bog'lanadi). Uzum yangilaydi: sotish narxi, komissiya %, qoldiq (FBO+FBS), sotilgan/qaytgan soni. **Tannarx faqat bo'sh bo'lsa** Uzum `purchasePrice` dan olinadi (0 = yo'q); sotuvchi kiritgan tannarx va nom o'zgarmaydi. Arxiv/o'chirilgan kartochkalar o'tkaziladi. Kunlik fon sinxron 04:40 (Toshkent), qo'lda — 10 daqiqada bir marta; bir biznesga bir vaqtda bitta sinxron. |
| COST-NULL | `products.unit_cost` endi bo'sh bo'lishi mumkin (import). Bunday mahsulot ro'yxatda «🟡 tannarx?», hisobga kirishda tannarx so'raladi; «🟡 Tannarxni kiritish» — ketma-ket tez kiritish. |
| UZ-COMMISSION | Uzum mahsulotidan hisob boshlansa: kanal = Uzum, komissiya = SKU komissiyasi (kanal stavkasidan ustun), komissiya qadami o'tkaziladi. |
| FILE-IMPORT | Botga .xlsx yoki .csv (≤ 5 MB, ≤ 5 000 qator). Ustunlar sarlavhadan avtomatik (uz/ru/en): nomi (majburiy), tannarx, narx, SKU/artikul, shtrix-kod. CSV: UTF-8/Windows-1251, `;` `,` tab. XLSX — o'z minimal o'quvchimiz (tashqi kutubxonasiz, XXE o'chiq, zip-bomb chegarasi 40 MB). Moslash: SKU → aynan nom → yangi. Fayldagi tannarx mavjudining ustidan yozadi (sotuvchining ongli kiritishi). `.xls` qo'llanmaydi. |
| LEAKS | «🩸 Foyda yo'qotishlari»: faol mahsulotlar bo'yicha 1 dona foyda (narx, tannarx, Uzum komissiyasi + saqlangan kanal stavkalari). Zarar va minimal marjadan past ro'yxati; stavkalar yo'q bo'lsa — «taxminiy» ogohlantirishi. Sotuv hajmi bo'yicha (davr) foyda — V3 (Uzum `finance/orders`). |
| WHAT-IF | Natijada «🔮 Agar…»: narx ±5/10%, tannarx +10%, komissiya +2 p.p., chegirma 10/20%, reklama 5% — foyda va farq. Saqlanmaydi. |

## 2026-11-23 — V3 (Uzum moliya, davr hisoboti, pul oqimi) qarorlari

| ID | Qaror |
|---|---|
| FIN-SYNC | Uzum sinxroni mahsulotlardan keyin moliyani ham o'qiydi: `GET /v1/finance/orders` (`group=false`, har do'kon alohida, size 1000) va `GET /v1/finance/expenses`. Birinchi marta — 90 kunlik tarix, keyin har safar oxirgi 35 kun qayta o'qiladi (status o'zgarishlari). Kalit: sotuv qatori — (do'kon, buyurtma, qator ID); xarajat — ID. Moliya xatosi mahsulot sinxronini buzmaydi (`finance_error`). V13 migratsiya. |
| FIN-CLASSIFY | Xarajat toifasi `code` bo'yicha (Kiril С/У = lotin C/Y): saqlash, qaytim saqlash, jarima, reklama, fotostudiya, yechish xizmati; `BALANCE_CORRECTION` — ichki o'tkazma (xarajat emas). Noma'lum kod — nom bo'yicha (штраф, реклам, хранен...), bo'lmasa «boshqa». Summa `abs(paymentPrice)`; `INCOME`, `CANCELED`, `REFUNDED` hisobga olinmaydi. |
| FIN-PNL | Davr hisoboti (7 / 30 kun, joriy / o'tgan oy, Toshkent vaqti): tushum = Σ narx × soni (faqat `TO_WITHDRAW` va `PARTIALLY_CANCELLED`); Uzum to'lovi = tushum − komissiya − logistika; tannarx — sotuvchi kiritgani, bo'lmasa qatordagi `purchasePrice`; sof foyda = to'lov − tannarx − Uzum xarajatlari − soliq − qadoqlash/boshqa (Uzum kanal stavkalaridan). Logistika ikki marta ayrilmaydi. Tannarxsiz birliklar va soliq yo'qligi — ochiq ogohlantirish. |
| FIN-CASH | Pul oqimi: `PROCESSING` qatorlari — «yo'ldagi pul» (kutilayotgan to'lov, bekor bo'lishi mumkin). Bekor qilingan qatorlar soni ko'rsatiladi. |
| FIN-PRODUCTS | Mahsulot kesimida hissa (to'lov − tannarx, Uzum xarajatlari va soliqdan oldin): eng foydali 5 ta va zarar keltirgan 5 ta. |
| FIN-RETENTION | Sotuv va xarajat qatorlari — tijoriy ma'lumot: to'liq o'chirishda (purge) o'chiriladi; biznes o'chirilsa CASCADE. |

## 2026-11-23 — Production readiness qarorlari

| ID | Qaror |
|---|---|
| PR-TOTP | Admin TOTP sirlari AES-256-GCM bilan (`SELLERFLOW_SECRETS_KEY`, V14). Kalit o'qimasa — login rad etiladi, 2FA qayta sozlanmaydi, shifrmatn saqlanadi; tiklash — to'g'ri kalit yoki `ADMIN_RECOVERY_*`. |
| PR-SECRETS | Sirlar `.env` yoki Docker secrets (`*_FILE`, `/run/secrets/<NOM>`) orqali; compose'da `TELEGRAM_REQUIRED=true` — token topilmasa ilova ishga tushmaydi. |
| PR-CVE | Bog'liqliklar patch versiyalarga ko'tarildi. spring-webmvc 6.2.19 dagi CVE-2026-47884 / 47890 — ilovada XsltView, view rendering va SSE ishlatilmagani sababli muddatli istisno (2027-02-28); asosiy yechim — Spring Boot 4 (H-1). |
| PR-PROXY | Admin uchun HTTPS — ixtiyoriy Caddy override; faqat `/admin/*` o'tkaziladi, `/actuator` tashqariga yopiq. Standart yo'l — SSH tunnel. |
| PR-CI | Backup/restore skript testlari CI'da `postgres:16` ichida (production bilan bir xil image, gpg tekshiruvi). |
| PR-SERVER | Beta uchun 4 GB RAM / 2 vCPU / 40 GB SSD tavsiya; 2 GB — faqat swap bilan. Hosting O'zbekistonda (Uzum WAF). |
