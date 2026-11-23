# SellerFlow — loyihaning to'liq holati

**Sana:** 2026-11-23 · **Repo:** `xudoyberdiyev-dev/sellerflow`, `main` (`58d4eea`) · **Holat:** V3 Closed Beta (yopiq beta)

> Qisqacha: V1, V1+, V2 va V3 kodda to'liq bajarilgan, 195 ta avtomatik test o'tadi.
> Lekin hali **haqiqiy serverda, haqiqiy Telegram bot va haqiqiy Uzum kaliti bilan sinalmagan.**
> V4, V5 va V6 boshlanmagan.

---

## 1. Loyiha nima haqida

SellerFlow — marketplace sotuvchilari (birinchi navbatda Uzum Market) uchun Telegram bot va web admin panel.
Maqsad: sotuvchi har bir mahsulotdan va har bir davrdan **haqiqatda qancha foyda qolishini** ko'rsin. Komissiya,
logistika, qaytarish, reklama, chegirma, saqlash va jarimalardan keyin qancha qolishi, narxni qanday qo'yish
kerakligi va qaysi mahsulot zarar keltirayotgani ko'rinadi.

**Foydalanuvchilar:**
- **Sotuvchi** — Telegram bot orqali ishlaydi.
- **Superadmin / Support** — web panel (`/admin/`) orqali ishlaydi.

---

## 2. Texnologiyalar

| Qism | Texnologiya |
|---|---|
| Backend + bot | Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway (V1–V13 migratsiyalar), Maven |
| Telegram | Long polling, har foydalanuvchi uchun navbat, dead-letter, advisory lock (bitta nusxa) |
| Admin panel | React + TypeScript + Vite, o'z dizayn tizimi (`admin-ui/`), Spring orqali `/admin/` da |
| Ishga tushirish | Docker Compose (app + db + backup), `.env` |
| CI | GitHub Actions: UI build, PostgreSQL 16 bilan testlar, osv-scanner, Dependabot |
| Hajm | Java ~12 400 qator, testlar — 28 fayl (195 test), admin UI ~4 100 qator |

**Sozlamalar fayli:** faqat `src/main/resources/application.properties`. `application.yml` loyiha egasi qarori bilan olib tashlangan.

---

## 3. Versiyalar holati

| Versiya | Holat | Commit |
|---|---|---|
| V1 — asosiy bot va admin panel | ✅ Kod tayyor | 1–8-bosqich auditlari |
| V1+ — qo'shimcha modullar TZ | ✅ Kod tayyor | `9aff7e9`, `39300f3` |
| V2 — Uzum integratsiyasi va import | ✅ Kod tayyor | `ce0615e` |
| V3 — Uzum moliya va davr hisoboti | ✅ Kod tayyor | `b1313d6` |
| V4 | ❌ Boshlanmagan | — |
| V5 | ❌ Boshlanmagan | — |
| V6 | ❌ Aniqlanmagan | — |

"Kod tayyor" degani: yozilgan, testlangan va `main` da turibdi. "Ishlatishga tayyor" degani esa server sinovidan keyin aniq bo'ladi.

---

## 4. Bajarilgan ishlar (batafsil)

### V1 — asosiy bot
- Ro'yxatdan o'tish: biznes nomi, ism, asosiy kanal, xizmat shartlariga rozilik (versiyalangan, shartlar o'zgarsa qayta rozilik).
- **💰 Foyda hisobi** (1 dona): tannarx, narx, komissiya, logistika, qadoqlash, reklama, soliq, xizmat haqi, boshqa xarajat.
  Har bir xarajat "summa / foiz / yo'q / bilmayman" bo'lishi mumkin. Natija "to'liq" yoki "taxminiy" deb belgilanadi.
- **🎯 Narx hisobi**: maqsadli foyda, maqsadli marja yoki zararsizlik narxi; yaxlitlash qadami 1 / 100 / 1 000 so'm; narx engine orqali qayta tekshiriladi.
- **📦 Mahsulotlarim**: qo'shish, tahrirlash, arxiv, qidiruv, SKU (biznes ichida noyob).
- **🗂 Tarix**: saqlangan hisoblar (snapshot va formula versiyasi bilan), qayta ochish, arxiv.
- **⚙️ Sozlamalar**: biznes nomi, ism, asosiy kanal, akkauntni o'chirish (ikki bosqichli, 30 kunlik kutish davri).
- Eski tugmalardan himoya (revision), qoralama 24 soat saqlanadi, rate limit, guruh chatda ishlamaydi.

### V1 — admin panel
- Kirish: email + parol + majburiy TOTP 2FA. Muhim amallarda joriy parol qayta so'raladi (step-up).
- Rollar: SUPERADMIN va SUPPORT.
- Bo'limlar: dashboard, sotuvchilar (bloklash, tiklash, to'liq o'chirish), sotuvchi ma'lumotiga **faqat sotuvchi roziligi bilan** vaqtinchalik kirish, audit jurnali, adminlar, support kontakti, tizim holati.
- Sotuvchilar ro'yxatini CSV qilib yuklab olish (faqat SUPERADMIN).

### V1 — xavfsizlik va ekspluatatsiya (8 bosqichli auditdan keyin)
- Tenant izolyatsiyasi (`business_id`), pul uchun faqat `BigDecimal`, optimistic lock.
- Kunlik shifrlangan backup, `restore.sh` (vaqtinchalik bazaga tiklab, keyin almashtiradi; `--rollback` bilan), `restore-drill.sh`.
- Backup monitoringi va Telegram'ga alert, health va Prometheus metrikalari, request firewall (64 KiB), login rate limit.
- Ma'lumotni saqlash muddatlari: o'chirish job'i, to'liq o'chirish (purge), ro'yxatdan o'tmaganlar 30 kunda tozalanadi.

### V1+ (engine 1.1.0)
- **Qaytarish xarajati** va **chegirma**. Chegirma tushumni kamaytiradi: foiz xarajatlar va marja chegirmadan keyingi narxdan hisoblanadi.
- **📌 Kanal stavkalari**: natijadan saqlanadi, keyingi hisobda avtomatik qo'llanadi.
- **📊 Narx variantlari**: zararsizlik va 10/20/30/40% marja uchun narxlar.
- **📉 Minimal marja** va undan past natijada ogohlantirish.
- **📤 CSV eksport**: mahsulotlar va hisoblar (10 daqiqada bir marta).

### V2
- **🟣 Uzum ulanishi** (Seller OpenAPI, faqat o'qish). Kalit xabari darhol o'chiriladi, kalit AES-256-GCM bilan shifrlanadi (`SELLERFLOW_SECRETS_KEY`). Uzish va yangi kalit kiritish mumkin.
- **Mahsulot sinxroni**: har bir SKU — nom, artikul, shtrix-kod, narx, komissiya, qoldiq, sotilgan va qaytgan soni. Har kecha avtomatik, qo'lda 10 daqiqada bir marta.
- **📄 Excel/CSV import** (ustunlar o'zbek/rus/ingliz sarlavhalaridan avtomatik aniqlanadi).
- **🟡 Tannarxni tez kiritish** — tannarxi yo'q mahsulotlar ketma-ket chiqadi.
- Uzum mahsulotidan hisob boshlansa, komissiya avtomatik qo'yiladi.
- **🩸 Foyda yo'qotishlari** — zarar va past marjali mahsulotlar.
- **🔮 Agar… simulyatori** — narx, tannarx, komissiya, chegirma yoki reklama o'zgarsa foyda qanday bo'lishi.

### V3
- **Uzum moliya sinxroni**: sotuv qatorlari va Uzum xarajatlari (avval 90 kun, keyin oxirgi 35 kun). Xarajatlar kod bo'yicha tasniflanadi.
- **📈 Davr hisoboti** (7 yoki 30 kun, joriy yoki o'tgan oy). Undagi qatorlar:
  - tushum;
  - komissiya va logistika;
  - Uzum to'lovi;
  - tannarx;
  - saqlash, jarima va reklama;
  - soliq;
  - **sof foyda va marja**.
- **💸 Yo'ldagi pul** (kutilayotgan to'lov), bekor qilinganlar soni.
- Eng foydali va zarar keltirgan mahsulotlar.

---

## 5. Hali qilinmagan yoki qisman qilingan ishlar

### 5.1. Keyingi versiyalar (TZ bo'yicha)

| Modul | TZ'dagi versiya | Holat |
|---|---|---|
| Wildberries / Ozon ulanishi | V3+ | ❌ (faqat Uzum qilingan) |
| 3.6 Raqobatchi narxlari | V4 | ❌ (rasmiy manba kerak) |
| 3.10 Health Score | V4 | ❌ |
| 3.11 Growth Simulator | V4 | ❌ |
| 3.12 AI maslahatchi | V4 | ❌ (AI xizmati hisobi kerak) |
| 3.4 Dead Stock (sotilmay turgan tovar) | V5 | ❌ |
| 3.7 Yetkazib beruvchilar | V5 | ❌ |
| 3.9 Xarid rejasi | V5 | ❌ |
| 3.5 Pul oqimi (to'liq) | V2 | 🟡 Faqat yo'ldagi pul va kutilayotgan to'lov; bashorat yo'q |
| Pullik tariflar va to'lov (Click/Payme) | V1 (TZ) | ❌ Avvalgi qaror: yopiq beta, faqat Free |
| Sotuvchi uchun web kabinet | — | ❌ Sotuvchi faqat botda ishlaydi |

### 5.2. Texnik qarzlar (keyinga qoldirilgan)

| Masala | Izoh |
|---|---|
| TOTP 2FA sirlari bazada ochiq | Backup shifrlangan; DB darajasida shifrlash uchun kalit boshqaruvi qarori kerak |
| Versiyalangan Docker image va registry yo'q | Rollback hozircha qo'lda (`git checkout` + `restore.sh --rollback`) |
| Base image'lar digest bilan pinlanmagan | Dependabot haftalik yangilaydi |
| Sirlar `docker inspect` da ko'rinadi | Docker secrets'ga o'tish kerak |
| Native UPDATE'lar `version` ni oshirmaydi | Hozircha xavf past |
| Spring Boot 4.x ga o'tish | 2–5 kunlik alohida ish |
| Admin UI mayda kamchiliklari | OtpInput siljishi, fokus, sessiya tugash ogohlantirishi — funksional xavf yo'q |
| Uzum API maydonlari | Rasmiy swagger va ochiq qo'llanma asosida; haqiqiy kalit bilan solishtirish kerak |

---

## 6. Loyiha egasi bajaradigan ishlar

1. **Server**: VPS (kamida 2 GB RAM). Uzum WAF xorijiy IP'ni bloklashi mumkin, kerak bo'lsa O'zbekistondagi hosting.
2. **`.env` ni to'ldirish**: `TELEGRAM_BOT_TOKEN`, `DB_PASSWORD` (yangi), `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` (yangi, 12+ belgi), `BACKUP_ENCRYPTION_PASSPHRASE`, `ALERT_TELEGRAM_CHAT_ID`, `SELLERFLOW_SECRETS_KEY` (`openssl rand -base64 32`, parol menejerida ham saqlang).
3. **Eski parollarni almashtirish** (ular ochiq repo tarixida ko'ringan). Repo'ni private qilishni o'ylab ko'ring.
4. **Backup nusxasini** server tashqarisiga muntazam ko'chirib turish.
5. **Jonli sinov**: botda barcha bo'limlar; Uzum kaliti bilan mahsulotlar, sotuvlar va davr hisobotini Uzum kabinetidagi hisobot bilan solishtirish.
6. **Yurist**: xizmat shartlari (`terms_uz.txt` — qoralama), maxfiylik siyosati, Uzum kaliti va moliya ma'lumotlarini qayta ishlash bandi, saqlash muddatlari, `DELETION_PURGE_DATA` qarori.
7. **Sinov sotuvchilari**: 3–5 ta sotuvchi.
8. **Keyinroq**: to'lov uchun Click/Payme shartnomasi va yuridik shaxs; AI xizmati hisobi; Wildberries/Ozon kalitlari.
9. **Javob kerak bo'lgan savollar**: pullik tarif limitlari nimani sanaydi; Spring Boot 4.x ga o'tish vaqti.

---

## 7. Serverga qo'yish (qisqa)

```bash
git clone https://github.com/xudoyberdiyev-dev/sellerflow.git && cd sellerflow
cp .env.example .env && nano .env      # 6-bo'limdagi qiymatlar
docker compose up -d --build           # birinchi marta 5–10 daqiqa
docker compose logs --tail 100 app     # "Telegram long polling started", "Started SellerFlowApplication"
```
- Bot: Telegram'da `/start`.
- Admin panel: `ssh -L 8080:localhost:8080 user@server` → http://localhost:8080/admin/ (birinchi kirishda 2FA QR).
- Bitta token bilan faqat bitta nusxa ishlashi kerak (aks holda logda `HTTP 409`).

---

## 8. Hujjatlar

| Fayl | Mazmuni |
|---|---|
| `README.md` | Umumiy tavsif, ishga tushirish |
| `docs/DECISIONS.md` | Barcha qarorlar (V1 … V3) |
| `docs/OPERATIONS.md` | Ekspluatatsiya: backup, restore, monitoring, Uzum (§6a) |
| `docs/AUDIT_FIX_REPORT.md`, `docs/AUDIT_PROGRESS.md` | Auditlar va ularning tuzatilishi |
| `docs/PROJECT_STATUS.md` | Shu hujjat |
