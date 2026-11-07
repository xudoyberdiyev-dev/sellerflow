# Superadmin dizaynini kodga ko'chirish rejasi

Manba: Claude Design eksporti (2026-11-07). U shu papkada:
- `tokens/` — dizayn tokenlari;
- `components/` — React primitivlari (`.jsx`, `.d.ts`, `.prompt.md`);
- `ui_kits/admin/` — bosiladigan prototip va `.dc.html` ekran artboardlari;
- `guidelines/` — qo'llanmalar;
- `README.md` — dizayn qoidalari.

Prototipni ko'rish uchun `ui_kits/admin/index.html` ni brauzerda oching. U React'ni CDN'dan yuklaydi, shuning uchun internet kerak.

**Maqsad:** `admin-ui/` dagi hozirgi panelni shu dizayn tizimiga to'liq o'tkazish va dizayn talab qilgan backend endpointlarini qo'shish. Har bir band `docs/AUDIT_PROGRESS.md` dagi «Dizayn» bosqichida kuzatiladi.

## Dizayn va backend o'rtasidagi farqlar — qabul qilingan qarorlar

| Dizayndagi narsa | Qaror | Sabab |
|---|---|---|
| Sotuvchining ismi va `@username` | Telegram ID ko'rsatiladi. Agar sotuvchi biznes nomi yoki ism kiritgan bo'lsa (`businesses.business_name`, `display_name`), ular ham ko'rsatiladi | Bot Telegram ismi va username'ni saqlamaydi |
| Telefon raqami (`+998 90 *** ** 17`) | Olib tashlanadi | CR-PHONE-01: telefon raqami so'ralmaydi va saqlanmaydi |
| «Adminni o'chirish» | «Adminni o'chirib qo'yish» — `DISABLED` holati | Audit yozuvlari adminga bog'langan. Hard delete qilinmaydi |
| Sotuvchidagi mahsulot va hisoblashlar soni | Ko'rsatiladi | Bu tijoriy qiymat emas, faqat son (agregat) |
| Lucide ikonkalari CDN'dan (`lucide-static`) | `lucide-react` npm paketi | CSP `script-src 'self'`. CDN ishlatilmaydi |
| Google Fonts (`tokens/fonts.css`) | `@fontsource/inter`, `@fontsource/jetbrains-mono` (self-host) | CSP va maxfiylik: tashqi so'rov yo'q |
| `ds-loader.js` + Babel standalone | Ishlatilmaydi. Komponentlar `admin-ui/src/components/` ga TypeScript qilib ko'chiriladi | Production build Vite orqali |
| Rolni va mavzuni almashtirish (prototip menyusi) | Rol — serverdan (`/auth/me`). Mavzu — foydalanuvchi sozlamasi (`localStorage`, try/catch bilan) | Prototip uchun qilingan qulaylik |
| «Prototip holati» almashtirgichi | Kiritilmaydi. Holatlar haqiqiy yuklanish, xato va 403 javoblaridan kelib chiqadi | — |

## Bandlar (tartib bo'yicha)

1. **D-1 Tokenlar va shriftlar.** `tokens/*.css` va `components/components.css` ni `admin-ui/src/styles/` ga ko'chirish. Shriftlarni self-host qilish. Tailwind faqat layout uchun qoladi (yoki butunlay olib tashlanadi). Qorong'i mavzu `[data-theme="dark"]` orqali.
2. **D-2 Komponentlar.** `components/**` dagi primitivlarni TSX'ga ko'chirish:
   - core, buttons, inputs, choices, display, cards, feedback, table, navigation, overlays, privacy;
   - `.d.ts` dagi props shartnomasi saqlanadi;
   - `format.js` → `format.ts` (sana Asia/Tashkent, pul «120 000 so'm»).
3. **D-3 App shell.**
   - Sidebar: guruhlar, yig'ilish, rolga qarab yashirish.
   - Topbar: breadcrumbs, ⌘K CommandPalette, tizim holati indikatori.
   - Toast stack, ConfirmDialog (`requireReason`, kamida 10 belgi, `reason` → API).
4. **D-4 Auth.** Login, 2FA ulash stepper (QR + secret + OTP), OTP kodi, bloklanish, majburiy parol almashtirish, sessiya tugashi.
5. **D-5 Backend endpointlari (dizayn uchun):**
   - `GET /sellers/{telegramUserId}`: profil metadatasi, shartlar versiyasi, mahsulot/hisoblash soni, oxirgi faollik, bloklash sababi (auditdan).
   - `/sellers`: bir nechta holat, kanal, sana oralig'i, saralash, holatlar bo'yicha sonlar, `GET /sellers/export.csv` (faqat metadata, auditga yoziladi).
   - `GET /dashboard?period=7|30|90`: KPI va o'zgarish %, kunlik seriyalar, kanallar, onboarding voronkasi, 7 kunlik retention. Buning uchun `users.last_seen_at` va onboarding bosqichlarini yozib borish kerak.
   - `/audit`: sana, bajaruvchi, amal, biznes filtrlari, CSV eksport.
   - `/grants`: holat tablari va sotuvchi filtri. `/grants/{id}/data`: sahifalash.
   - `/auth/me`: `expiresAt`. `GET /auth/sessions`, `POST /auth/sessions/revoke-others`.
   - `/health`: backup holati (`/backups/status.json`), baza javob vaqti.
   - Support: oxirgi o'zgartirgan admin va vaqt.
   - Bildirishnomalar: audit asosidagi so'nggi muhim hodisalar.
6. **D-6 Sahifalar** (`ui_kits/admin/*.dc.html` va `*.jsx` bo'yicha):
   - Boshqaruv paneli, Tizim holati;
   - Sotuvchilar (jadval va quick-view drawer);
   - Sotuvchi kartasi: tablar, PrivacyGuard, «Kirish so'rash» modali + TelegramPreview, ReadOnlyBanner + countdown + re-lock;
   - Kirish so'rovlari va READ-ONLY ko'ruvchi;
   - Qo'llab-quvvatlash (bot preview bilan);
   - Audit jurnali, Administratorlar, Profil va xavfsizlik.
7. **D-7 Holatlar, moslashuv, sifat.**
   - Har sahifada skeleton, bo'sh, xato va 403 holatlari («Holatlar kutubxonasi» bo'yicha).
   - Planshet va mobil, qorong'i mavzu, fokus tartibi.
   - Playwright skrinshot-tekshiruvlari, lint va build.
   - Eski Tailwind komponentlari olib tashlanadi.

## Qabul mezonlari
- Barcha ekranlar `ui_kits/admin` dagi artboardlarga vizual mos: ranglar, o'lchamlar, matnlar.
- CSP buzilmaydi: tashqi script, shrift yoki ikonka so'rovi yo'q.
- Tijoriy qiymat faqat faol grant bilan READ-ONLY ko'ruvchida. Muddat tugaganda ma'lumot ekrandan tozalanadi.
- SUPPORT roli uchun xavfli amallar yashirin (disabled emas).
- `npm run build`, `npm run lint`, `mvn verify` yashil. Yangi endpointlarning har biri test bilan qoplangan.
