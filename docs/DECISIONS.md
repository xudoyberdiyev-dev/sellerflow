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
| CR-ADMIN-01 | Superadmin tijoriy ma'lumotlarga kira olmaydi: admin API'da tannarx, foyda yoki tarix qaytarilmaydi. Rozilik asosidagi 30–60 daqiqalik READ-ONLY kirish — PRD-07 da. | V1.0 da kirish umuman yo'q |
| UX-DEC-37/38/39 | Sozlamalar: yagona markaz; maydonlar alohida saqlanadi; kanal o'zgarishi tasdiq bilan; o'chirish so'rovi ikki bosqichli. | Qabul qilindi ("default" — DRAFT A variantlari) |

## Texnik qarorlar (kod darajasida)

- **Pul arifmetikasi:** `BigDecimal`. Pul qiymatlari — 2 kasr xona, HALF_UP. Foizdan hisoblangan summa (masalan, 1,5% × narx) tiyingacha yaxlitlanadi. Marja 2 kasr xonagacha.
- **Foiz** kiritishda "10" = 10%. Ichkarida ulushga (0.10) o'giriladi.
- **Kiritish normalizatsiyasi:** `120000`, `120 000`, `120.000` va `1,250,000` qabul qilinadi. 3 xonali guruhlar ming ajratuvchi deb olinadi; boshqa hollarda vergul o'nlik belgisi (`1,5`). Ko'pi bilan 2 kasr xona. Maksimal qiymat 999 999 999 999.
- **Narxni tekshirish:** yaxlitlangan narx profit engine orqali qayta hisoblanadi. Tiyin yaxlitlash sababli maqsadga yetmasa, narx bir qadam oshiriladi (eng ko'pi 50 marta).
- **Tarix sahifalash:** offset bilan, lekin ro'yxat ochilgan paytdagi eng katta `id` (anchor) bilan cheklanadi. Shu sababli parallel Save sahifalarni siljitmaydi.
- **Eski tugmalar:** wizard tugmalari `revision` va qadamni o'z ichiga oladi. Ulardan biri mos kelmasa — "tugma eskirgan" deyiladi va hech narsa o'zgarmaydi.
- **Idempotentlik:** `processed_updates` (Telegram update_id), `calculation_history.result_id UNIQUE`, `products.idempotency_key UNIQUE`, faollashtirishda `businesses.owner_user_id UNIQUE`.
- **Konkurentlik:** update'lar bitta poller oqimida ketma-ket qayta ishlanadi. V1.0 beta hajmi uchun yetarli.
- **SKU:** biznes ichida katta-kichik harfdan qat'i nazar noyob. Arxivlangan mahsulot SKU'si ham band hisoblanadi.
- **"Bitta faol jarayon":** faqat ko'rish bo'limlariga (ro'yxatlar, tariflar, yordam) o'tish qoralamani o'chirmaydi, u to'xtatib qo'yiladi. Yangi kiritish jarayonini boshlashda saqlanmagan ma'lumot bo'lsa, tasdiq so'raladi (UX-DEC-17).

## Ochiq qolganlar (ishga tushirishdan oldin)

1. **Yuridik tekshiruv:** xizmat shartlari (`terms_uz.txt` — QORALAMA v0.1), maxfiylik siyosati, O'RQ-547 / O'RQ-1125, hosting joylashuvi.
2. **30 kunlik o'chirish davridan keyin:** ma'lumotlarni qayta ishlash va o'chirish jarayoni (CLOSED holatiga o'tkazish) avtomatlashtirilmagan. Retention siyosati kerak.
3. **Web Superadmin (PRD-07):** UI, RBAC, rozilik asosidagi vaqtinchalik kirish.
4. **PRD-06 Billing:** pullik tariflar (V1.1).
5. **Asl PRD-01 / 02.01 / 02.02 matnlari:** kodni ular bilan qator-ma-qator solishtirish (traceability).
6. **Legacy `UX-COST` (Xarajat profillari)** reyestri bilan solishtirish.
7. **Monitoring:** alertlar, avtomatik backup, restore drill.
8. **Rate limiting:** bitta foydalanuvchi uchun spam himoyasi yo'q.
