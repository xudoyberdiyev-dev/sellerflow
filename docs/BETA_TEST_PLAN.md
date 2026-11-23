# SellerFlow V3 — yopiq beta sinov rejasi (3–5 sotuvchi)

**Sana:** 2026-11-23 · Shart: `PRODUCTION_READINESS_PLAN.md` dagi CRITICAL ishlar (C-1…C-5) bajarilgan.

## 1. Maqsad va muddat
- **Maqsad:** real sotuvchilar bilan uch narsani tasdiqlash:
  1. bot oqimlari tushunarli va xatosiz;
  2. Uzum ma'lumotlari to'g'ri keladi;
  3. davr hisobotidagi summalar Uzum kabineti bilan mos.
- **Muddat:** 3 hafta. 1-hafta — 1–2 sotuvchi (ichki), 2–3-hafta — 3–5 sotuvchi.
- **Ishtirokchilar:**
  - **Loyiha egasi** — beta menejer: sotuvchilar bilan aloqa, fikr yig'ish.
  - **Claude** — xatolarni tuzatish, ma'lumotni solishtirish skriptlari.
  - **Sotuvchilar.**

## 2. Beta'dan oldin: smoke test (egasi, 30 daqiqa)
1. `/start` → ro'yxatdan o'tish → shartlarga rozilik.
2. 💰 Foyda hisobi (chegirma bilan) → saqlash → 🗂 Tarix'da ko'rinishi.
3. 🎯 Narx hisobi → 📊 Narx variantlari → 🔮 Agar….
4. 📦 Mahsulot qo'shish, tahrirlash, arxivlash; 📄 Excel import (namuna fayl).
5. 🟣 Uzum: kalit → xabar o'chdi → mahsulotlar keldi → 🟡 tannarx kiritish → 📈 hisobot.
6. Sozlamalar → 📤 CSV eksport; 🔌 Uzum'ni uzish.
7. Admin panel: login + 2FA, sotuvchilar ro'yxati, rozilik asosida kirish so'rovi (sotuvchi tasdiqlaydi) → ko'rish → bekor qilish; audit jurnalida yozuvlar.
8. Backup: `backups/status.json` yangi; alert chatga sinov xabari (backup'ni vaqtincha buzib).

Har bir qadam PASS yoki FAIL deb belgilanadi. FAIL bo'lsa, beta boshlanmaydi.

## 3. Sotuvchilarni tanlash
- Uzum'da faol sotuvchilar: oyiga kamida 30 ta buyurtma, 10–500 SKU, FBO va/yoki FBS.
- O'z tannarxlarini biladi (Excel'da bo'lsa yaxshi).
- Ishonchli tanish. Ular bilan yozma kelishuv bo'ladi: beta, bepul, ma'lumot maxfiyligi, istalgan payt chiqish.

## 4. Sinov akkauntlari va ro'yxatdan o'tkazish
- Har sotuvchi **o'z Telegram akkaunti** bilan botda ro'yxatdan o'tadi. Alohida "test" akkaunt kerak emas — real ma'lumot bilan sinaymiz.
- Egasi uchun ichki sinovga alohida bot (test token) va alohida server/`.env` — production bilan aralashtirilmaydi.
- Ro'yxatdan o'tish yo'riqnomasi (sotuvchiga yuboriladi):
  1. Botni oching → `/start` → biznes nomi, ism, asosiy kanal (Uzum) → shartlar.
  2. Sozlamalar → 🟣 Uzum'dan olish → yo'riqnoma bo'yicha kalit.

## 5. Uzum'ni ulash yo'riqnomasi (sotuvchi uchun)
1. seller.uzum.uz → **Настройки → API-ключи → Создать ключ**. Ruxsat: faqat o'qish yetarli.
2. Kalitni nusxalang → botda **🔑 Kalitni kiritish** → kalitni bitta xabar qilib yuboring.
3. Bot xabarni darhol o'chiradi va «Uzum ulandi» deydi. 1 daqiqagacha mahsulotlar va sotuvlar keladi.
4. Kalitni **hech kimga yubormang**, jumladan support'ga ham. Kerak bo'lsa botda «🔌 Uzish» va Uzum kabinetida kalitni o'chirish mumkin.

## 6. Tannarxlarni kiritish
- **Variant A:** 🟡 Tannarxni kiritish — mahsulotlar ketma-ket chiqadi, raqam yoziladi.
- **Variant B:** 📄 Excel — «Artikul» va «Tannarx» ustunli fayl. SKU bo'yicha moslashadi.
- **Maqsad:** 1-hafta oxiriga sotuvlarning ≥ 90% i tannarxli bo'lsin (hisobotda «tannarx yo'q» ogohlantirishi yo'qolsin).

## 7. Tekshiruvlar va o'lchovlar

### 7.1 Sinxronlash
| Tekshiruv | Qanday | Qabul mezoni |
|---|---|---|
| Mahsulotlar soni | Botdagi SKU soni va Uzum kabinetidagi faol SKU | farq 0 (arxivlanganlardan tashqari) |
| Narx va komissiya | 10 ta tasodifiy SKU | 100% mos |
| Qoldiq | 10 ta SKU | mos (FBO + FBS) |
| Takroriy sinxron | ikki marta yangilash | dublikat yo'q |
| Xatolar | `marketplace_connections.last_error` / `finance_error` | 3 hafta davomida tizimli xato yo'q |

### 7.2 Moliyaviy hisobotni solishtirish (eng muhim)
Har sotuvchi bilan **o'tgan oy** uchun bir marta, beta oxirida yana bir marta:

| Ko'rsatkich | Bot (📈 O'tgan oy) | Uzum kabineti (Финансы / отчёт) | Qabul |
|---|---|---|---|
| Yakunlangan buyurtmalar / dona | | | farq ≤ 1% |
| Tushum | | | farq ≤ 1% |
| Komissiya | | | farq ≤ 1% |
| Logistika | | | farq ≤ 1% |
| Uzum to'lovi (к выплате) | | | farq ≤ 1% |
| Saqlash, jarimalar, reklama | | | farq ≤ 2% |

- Farq chegaradan oshsa: Claude anonimlashtirilgan qatorlar bilan sababini topadi (maydon semantikasi — H-2) va tuzatadi. Keyin solishtirish qayta o'tkaziladi.
- Sof foydani sotuvchi o'z hisobi bilan solishtiradi. Bu sifat bahosi: «ishonarli» yoki «ishonarli emas, sababi …».

### 7.3 Barqarorlik
- Bot javob vaqti: odatda < 2 soniya.
- 3 hafta ichida konteynerlar qayta ishga tushishi — sababsiz bo'lmasligi kerak.
- Kunlik backup — 21/21 muvaffaqiyatli.
- Bir marta restore drill (`scripts/restore-drill.sh`).

## 8. Xatolarni qayd etish
- **Qayerda:** GitHub Issues (repo private bo'lsa) yoki egasining jadvali. Maydonlar: sana, sotuvchi (taxallus, ism emas), qadam, kutilgan, haqiqiy, skrinshot (**summalar va mahsulot nomlari bo'yashi mumkin**).
- **Darajalar:**
  - **S1** — noto'g'ri pul hisobi yoki ma'lumot sizishi: 24 soatda tuzatish, kerak bo'lsa botni to'xtatish.
  - **S2** — funksiya ishlamaydi: 3 kun.
  - **S3** — noqulaylik: beta oxirida.
- **Maxfiylik:**
  - sotuvchi Uzum kaliti, tannarxlari va moliyaviy ma'lumoti issue'ga yozilmaydi;
  - Claude real ma'lumotni faqat sotuvchi roziligi bilan va anonimlashtirilgan holda ko'radi;
  - admin panelda ma'lumot faqat rozilik asosidagi vaqtinchalik kirish orqali ko'riladi.

## 9. Fikr yig'ish
- **1-hafta oxiri:** 10 daqiqalik qo'ng'iroq — nima tushunarsiz, nima ko'p vaqt oldi.
- **Beta oxiri:** qisqa so'rovnoma (1–5 baho):
  1. Ro'yxatdan o'tish va Uzum'ni ulash osonmi?
  2. Hisobotdagi raqamlarga ishonasizmi?
  3. Qaysi funksiya eng foydali? Qaysi biri keraksiz?
  4. Pullik bo'lsa, oyiga qancha to'lagan bo'lardingiz?
  5. Nima yetishmayapti?
- **Ochiq savol:** «Bu botsiz qanday qaror qabul qilardingiz va bot nimani o'zgartirdi?»

## 10. Beta'dan chiqish mezonlari
Barchasi bajarilishi kerak:
1. S1 xatolar — 0 (ochiq), S2 — 0 (ochiq).
2. Har bir sotuvchi uchun 7.2 dagi solishtirish farqlari chegarada.
3. 21 kunlik backup va bitta restore drill muvaffaqiyatli.
4. Kamida 3 sotuvchi 2 hafta davomida botdan foydalangan (har haftada ≥ 2 marta).
5. So'rovnomada «raqamlarga ishonaman» o'rtacha ≥ 4.
6. Yuridik hujjatlar yurist tomonidan tasdiqlangan va `TERMS_VERSION` yangilangan.
7. `PRODUCTION_READINESS_PLAN.md` dagi H-1 (Spring Boot 4) bajarilgan yoki istisno muddati ichida va reja bilan.

## 11. Huquqiy va operatsion tayyorgarlik (yurist uchun talablar ro'yxati)
**Muhim:** quyidagi hujjatlar yurist tekshirmaguncha **yakuniy emas**. Hozirgi `terms_uz.txt` — qoralama v0.1.

| Hujjat | Tarkibi (talab) |
|---|---|
| Foydalanish shartlari | xizmat tavsifi (hisob — tavsiya, kafolat emas); beta holati; javobgarlik chegarasi; Uzum kalitini berish sotuvchining ixtiyori; to'xtatish va o'chirish tartibi |
| Maxfiylik siyosati | qaysi ma'lumot yig'iladi (Telegram ID, ism, biznes nomi, mahsulotlar, tannarx, Uzum sotuv va xarajatlari); maqsad; saqlash joyi (server mamlakati); uchinchi shaxslarga berilmasligi; O'RQ-547 talablari |
| Saqlash muddatlari | audit 365 kun; backup'lar 14 kun (o'chirilgan ma'lumot backup'da 14 kungacha); ro'yxatdan o'tmagan yozuvlar 30 kun; akkaunt o'chirish so'rovidan 30 kun keyin yopiladi; `DELETION_PURGE_DATA` qarori |
| Akkauntni o'chirish | botda ikki bosqichli; 30 kunlik bekor qilish imkoniyati; Uzum kaliti darhol o'chiriladi; qaysi yozuvlar dalil sifatida qoladi (rozilik, audit) |
| Moliyaviy ma'lumotlarni qayta ishlash | tannarx, sotuv va xarajatlar tijoriy sir sifatida; admin faqat sotuvchi roziligi bilan va muddatli ko'radi; eksport faqat sotuvchiga |
| Uzum API shartlari | Uzum Seller OpenAPI'dan foydalanish shartlari va ruxsatlari (sotuvchi o'z kaliti bilan, faqat o'qish); Uzum bilan rasmiy kelishuv kerakmi — yurist va Uzum bilan aniqlash |
| Support va aloqa | `SUPPORT_LABEL` / `SUPPORT_URL`; javob muddati (beta: 1 ish kuni); support kalit yoki parol so'ramaydi |
| Incident response | `PRODUCTION_SECURITY.md` §5; ma'lumot sizganda sotuvchilarni va vakolatli organni xabardor qilish tartibi va muddati |
