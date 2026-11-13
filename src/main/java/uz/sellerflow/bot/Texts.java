package uz.sellerflow.bot;

/**
 * Barcha bot matnlari bir joyda (uz-Latn, CR-LOCALE-01). Keyinchalik ru / uz-Cyrl qo'shish uchun
 * shu klass resurs fayllarga ko'chiriladi. Matnlar HTML parse_mode uchun.
 */
public final class Texts {
    private Texts() {}

    // ------------------------------------------------ asosiy menyu (UX-NAV: nomlarni tasdiqsiz o'zgartirmang)
    public static final String BTN_PROFIT = "💰 Foydani hisoblash";
    public static final String BTN_PRICE = "🏷 Narxni aniqlash";
    public static final String BTN_PRODUCTS = "📦 Mahsulotlarim";
    public static final String BTN_HISTORY = "📊 Hisoblashlar tarixi";
    public static final String BTN_SETTINGS = "⚙️ Sozlamalar";
    public static final String BTN_TARIFFS = "⭐ Tariflar";

    public static final String MAIN_MENU = "🏠 <b>Bosh menyu</b>\nKerakli bo‘limni tanlang.";
    public static final String USE_MENU = "Iltimos, pastdagi menyudan bo‘lim tanlang yoki /help ni bosing.";
    public static final String USE_MENU_WITH_DRAFT =
            "Hozir sizda tugallanmagan qoralama bor. Davom ettirish uchun tugmani bosing yoki menyudan bo‘lim tanlang.";
    public static final String CANCELLED = "❎ Joriy jarayon bekor qilindi.";
    public static final String NOTHING_TO_CANCEL = "Bekor qilinadigan faol jarayon yo‘q.";
    public static final String STALE = "Bu tugma eskirgan. Iltimos, oxirgi xabardagi tugmalardan foydalaning.";
    public static final String DRAFT_EXPIRED =
            "⌛ Oldingi qoralamaning muddati tugadi (oxirgi kiritishdan 24 soat o‘tdi). Yangidan boshlashingiz mumkin.";
    public static final String GENERIC_ERROR =
            "Kechirasiz, xatolik yuz berdi. Iltimos, qaytadan urinib ko‘ring. Ma’lumotlaringiz saqlangan holatda qoladi.";
    public static final String NOT_FOUND = "Bu yozuv topilmadi yoki sizga tegishli emas.";
    public static final String GROUP_CHAT =
            "SellerFlow’dan foydalanish uchun botga <b>shaxsiy chatda</b> yozing. Guruhda biznes ma’lumotlari ko‘rsatilmaydi.";

    // ------------------------------------------------ almashtirish / davom ettirish
    public static final String SWITCH_CONFIRM =
            "⚠️ Sizda saqlanmagan ma’lumot bor (<b>%s</b>). Yangi jarayonni boshlasangiz, u o‘chib ketadi.\n\nNima qilamiz?";
    public static final String BTN_SWITCH_YES = "🗑 Tashlab, davom etish";
    public static final String BTN_SWITCH_NO = "↩️ Qoralamaga qaytish";
    public static final String RESUME_PROMPT = "📝 Sizda tugallanmagan <b>%s</b> qoralamasi bor. Nima qilamiz?";
    public static final String BTN_CONTINUE = "▶️ Davom ettirish";
    public static final String BTN_RESTART = "🔄 Yangidan boshlash";

    // ------------------------------------------------ umumiy tugmalar
    public static final String BTN_BACK = "⬅️ Ortga";
    // M-26: onboarding tugallanmagan foydalanuvchi uchun ma'lumotni o'chirish
    public static final String BTN_WIPE = "🗑 Bekor qilish va ma’lumotni o‘chirish";
    public static final String WIPE_CONFIRM =
            "🗑 <b>Ro‘yxatdan o‘tishni bekor qilish</b>\n\nKiritgan ma’lumotlaringiz (qoralama, biznes nomi, ism) o‘chiriladi va "
                    + "Telegram ID’ingiz bu yozuvdan ajratiladi. Agar shartlarga rozilik bergan bo‘lsangiz, rozilik yozuvi qonuniy dalil "
                    + "sifatida shaxssiz holda saqlanadi.\n\nIstalgan payt /start bilan qaytadan boshlashingiz mumkin. Davom etasizmi?";
    public static final String BTN_WIPE_CONFIRM = "Ha, o‘chirilsin";
    public static final String WIPE_DONE = "✅ Ma’lumotlaringiz o‘chirildi. Qaytadan boshlash uchun /start yuboring.";
    public static final String BTN_MAIN = "🏠 Bosh menyu";
    public static final String BTN_CANCEL = "✖️ Bekor qilish";
    public static final String BTN_SKIP = "⏭ O‘tkazib yuborish";
    public static final String BTN_SAVE = "💾 Saqlash";
    public static final String BTN_EDIT = "✏️ Tahrirlash";
    public static final String BTN_CONFIRM = "✅ Tasdiqlash";
    public static final String BTN_TARIFFS_INLINE = "⭐ Tariflar";

    // ------------------------------------------------ holatlar
    public static final String ACCOUNT_SUSPENDED =
            "⛔ Akkauntingiz vaqtincha cheklangan. Biznes amallari hozircha mavjud emas.\nSavollar bo‘lsa, /help orqali yordam bo‘limiga o‘ting. "
                    + "Ma’lumotlaringizni o‘chirishni so‘rash: /delete";
    public static final String ACCOUNT_DELETION_REQUESTED =
            "🗑 Akkauntni o‘chirish so‘rovingiz qabul qilingan. 30 kunlik kutish davri amal qilmoqda — bu davrda biznes amallari to‘xtatilgan.";
    public static final String DELETION_CANCEL_PROMPT =
            "Fikringiz o‘zgardimi? 30 kun ichida so‘rovni shu yerda bekor qilishingiz mumkin — ma’lumotlaringiz joyida qoladi.";
    public static final String BTN_DELETION_CANCEL = "↩️ O‘chirish so‘rovini bekor qilish";
    public static final String DELETION_CANCELLED = "✅ O‘chirish so‘rovi bekor qilindi. Akkauntingiz yana faol.";
    public static final String ACCOUNT_CLOSED =
            "Bu akkaunt yopilgan.\n\nXohlasangiz, yangi akkaunt ochishingiz mumkin. Eski akkauntdagi ma’lumotlar yangisiga <b>o‘tkazilmaydi</b>.";
    public static final String BTN_REOPEN = "🆕 Yangi akkaunt ochish";
    public static final String RATE_LIMITED = "⏳ Juda ko‘p so‘rov yuborildi. Iltimos, bir necha soniya kuting.";
    public static final String RECONSENT =
            "📄 <b>Xizmat shartlari yangilandi</b> (versiya: %s)\n\n%s\n\nDavom etish uchun yangi shartlarga <b>aniq rozilik</b> bering. "
                    + "Ma’lumotlaringiz saqlangan holatda qoladi.";
    public static final String RECONSENT_DONE = "✅ Rozilik qabul qilindi. Davom etishingiz mumkin.";
    public static final String RECONSENT_DECLINED =
            "Yangi shartlarga rozilik berilmaguncha biznes amallari mavjud emas. Ma’lumotlaringiz o‘chirilmaydi. "
                    + "Xohlasangiz, akkauntni o‘chirishni so‘rashingiz mumkin.";
    public static final String FEATURE_LIMITED =
            "🔒 Bu imkoniyat joriy tarifingizda mavjud emas yoki limit tugagan.";

    // ------------------------------------------------ onboarding
    public static final String WELCOME =
            "👋 <b>SellerFlow’ga xush kelibsiz!</b>\n\n"
                    + "Bot mahsulotingizdan <b>haqiqatda qancha foyda qolishini</b> va uni <b>nechaga sotish kerakligini</b> hisoblab beradi — "
                    + "komissiya, logistika, reklama va boshqa xarajatlarni hisobga olgan holda.";
    public static final String BTN_START = "🚀 Boshlash";
    public static final String BTN_ABOUT = "ℹ️ SellerFlow haqida";
    public static final String ABOUT =
            "ℹ️ <b>SellerFlow nima qiladi?</b>\n\n"
                    + "• Bir dona mahsulot foydasini hisoblaydi: narx − tannarx − barcha ma’lum xarajatlar.\n"
                    + "• Maqsadli foyda yoki marja uchun kerakli sotish narxini topadi.\n"
                    + "• Mahsulotlaringiz va saqlagan hisoblashlaringizni bir joyda saqlaydi.\n\n"
                    + "Noma’lum xarajatlar hech qachon jimgina 0 deb olinmaydi — bunday natija «taxminiy» deb belgilanadi.\n"
                    + "SellerFlow soliq maslahatchisi emas va marketplace’lar bilan avtomatik ulanmagan: qiymatlarni o‘zingiz kiritasiz.";
    public static final String CONSENT =
            "📄 <b>Xizmat shartlari</b> (versiya: %s)\n\n%s\n\n"
                    + "Davom etish uchun shartlarga <b>aniq rozilik</b> bering.";
    public static final String BTN_AGREE = "✅ Roziman";
    public static final String BTN_DECLINE = "❌ Rozi emasman";
    public static final String CONSENT_DECLINED =
            "Rozilik berilmasa, SellerFlow’dan foydalanib bo‘lmaydi. Fikringiz o‘zgarsa, shartlarni qayta ko‘rishingiz mumkin.";
    public static final String BTN_TERMS_AGAIN = "📄 Shartlarni qayta ko‘rish";
    public static final String ASK_CHANNEL = "🛒 <b>Asosan qayerda sotasiz?</b>\nBu birlamchi savdo kanalingiz bo‘ladi (keyin Sozlamalarda o‘zgartirish mumkin).";
    public static final String ASK_BUSINESS_NAME =
            "🏪 Biznesingiz nomini yozing (ixtiyoriy, 80 belgigacha).\nO‘tkazib yuborsangiz, «Mening biznesim» deb ko‘rsatiladi.";
    public static final String ASK_DISPLAY_NAME =
            "🙂 Sizga qanday murojaat qilaylik? (ixtiyoriy, 60 belgigacha)\nO‘tkazib yuborsangiz, «Sotuvchi» deb ko‘rsatiladi.";
    public static final String ONB_REVIEW =
            "🔎 <b>Ma’lumotlarni tekshiring</b>\n\n"
                    + "🛒 Kanal: <b>%s</b>\n🏪 Biznes nomi: <b>%s</b>\n🙂 Ism: <b>%s</b>\n"
                    + "🌐 Til / valyuta / vaqt: o‘zbekcha / UZS / Asia/Tashkent\n⭐ Tarif: Free (Beta)\n\n"
                    + "Hammasi to‘g‘ri bo‘lsa, «Faollashtirish»ni bosing.";
    public static final String BTN_ACTIVATE = "✅ Faollashtirish";
    public static final String BTN_EDIT_CHANNEL = "✏️ Kanal";
    public static final String BTN_EDIT_BNAME = "✏️ Biznes nomi";
    public static final String BTN_EDIT_DNAME = "✏️ Ism";
    public static final String ACTIVATED =
            "🎉 <b>Tayyor!</b> Akkauntingiz faollashtirildi. Tarifingiz: <b>Free (Beta)</b>.\n\nPastdagi menyudan boshlang — masalan, «💰 Foydani hisoblash».";

    // ------------------------------------------------ hisoblash wizard
    public static final String PROFIT_TITLE = "💰 <b>Foydani hisoblash</b>";
    public static final String PRICE_TITLE = "🏷 <b>Narxni aniqlash</b>";
    public static final String ASK_COST = "1 dona mahsulotning <b>tannarxini</b> kiriting (so‘m).\nMasalan: <code>120000</code>";
    public static final String ASK_SALE_PRICE = "1 dona uchun <b>sotish narxini</b> kiriting (so‘m).\nMasalan: <code>200000</code>";
    public static final String ASK_COMMISSION = "🏬 <b>Marketplace komissiyasi</b> qanday?";
    public static final String ASK_LOGISTICS = "🚚 <b>Logistika</b> (1 dona uchun) xarajati bormi?";
    public static final String ASK_PERCENT = "Foizni kiriting (sotish narxidan, %). Masalan: <code>10</code> yoki <code>1,5</code>";
    public static final String ASK_AMOUNT = "Summani kiriting (1 dona uchun, so‘m). Masalan: <code>5000</code>";
    public static final String BTN_PERCENT = "📊 Foiz (%)";
    public static final String BTN_AMOUNT = "💵 Summa";
    public static final String BTN_ZERO = "0️⃣ Yo‘q (0)";
    public static final String BTN_UNKNOWN = "❔ Hozir bilmayman";
    public static final String EXTRAS =
            "➕ <b>Qo‘shimcha xarajatlar</b> (ixtiyoriy)\n\n%s\n\n"
                    + "Kiritmagan xarajatlaringiz 0 deb olinmaydi — natija «taxminiy» bo‘ladi. "
                    + "Agar ular haqiqatan yo‘q bo‘lsa, «Qolganlari yo‘q (0)» ni bosing.";
    public static final String BTN_EXTRAS_ZERO = "0️⃣ Qolganlari yo‘q (0)";
    public static final String BTN_NEXT = "➡️ Davom etish";
    public static final String ASK_EXTRA = "<b>%s</b> xarajati qanday?";
    public static final String TAX_NOTE =
            "\n\nℹ️ Bu soliq maslahati emas. Soliq foydadan yoki boshqa bazadan hisoblansa, 1 dona uchun aniq summani kiriting; bilmasangiz «Hozir bilmayman»ni tanlang.";
    public static final String ASK_GOAL = "🎯 Qaysi maqsad bo‘yicha narx topamiz?";
    public static final String BTN_GOAL_PROFIT = "💵 Maqsadli foyda summasi";
    public static final String BTN_GOAL_MARGIN = "📈 Maqsadli marja (%)";
    public static final String BTN_GOAL_BE = "⚖️ Zararsizlik nuqtasi";
    public static final String ASK_TARGET_PROFIT = "💵 1 dona uchun <b>kamida qancha foyda</b> qolishi kerak? (so‘m)\nMasalan: <code>25000</code>";
    public static final String ASK_TARGET_MARGIN =
            "📈 <b>Maqsadli marja</b> necha foiz? (0–99,99)\nMarja = foyda / sotish narxi. Masalan: <code>20</code>";
    public static final String ASK_NAME = "🏷 Hisoblash uchun mahsulot nomini yozing (ixtiyoriy, 120 belgigacha).";
    public static final String BTN_NO_NAME = "🚫 Nomsiz";
    public static final String ASK_CALC_CHANNEL = "🛒 Bu hisoblash qaysi savdo kanali uchun?\n(Bu biznesingizning asosiy kanalini o‘zgartirmaydi.)";
    public static final String BTN_CALCULATE = "🧮 Hisoblash";
    public static final String BTN_E_COST = "✏️ Tannarx";
    public static final String BTN_E_PRICE = "✏️ Sotish narxi";
    public static final String BTN_E_COMM = "✏️ Komissiya";
    public static final String BTN_E_LOG = "✏️ Logistika";
    public static final String BTN_E_EXTRAS = "✏️ Qo‘shimcha";
    public static final String BTN_E_TARGET = "✏️ Maqsad";
    public static final String BTN_E_GOAL = "✏️ Usul";
    public static final String BTN_E_NAME = "🏷 Nom";
    public static final String BTN_E_CHANNEL = "🛒 Kanal";
    public static final String SAVED = "✅ Natija tarixga saqlandi.";
    public static final String ALREADY_SAVED = "Bu natija allaqachon saqlangan — takroriy yozuv yaratilmadi.";
    public static final String BTN_TO_PROFIT = "💰 Foyda kalkulyatorida ochish";
    public static final String PARTIAL_WARNING =
            "⚠️ <b>Taxminiy natija.</b> Quyidagi xarajatlar noma’lum yoki kiritilmagan: %s. Ular hisobga qo‘shilmagan, lekin bu ularning 0 ekanini anglatmaydi.";
    public static final String DISCLAIMER =
            "<i>Bu hisob-kitob soliq hisoboti yoki real daromad kafolati emas.</i>";

    // ------------------------------------------------ validatsiya
    public static final String ERR_NUMBER =
            "❗ Son tushunilmadi. Faqat raqam yozing, masalan: <code>120000</code>, <code>120 000</code> yoki <code>1,5</code>.";
    public static final String ERR_PRICE_POSITIVE = "❗ Sotish narxi 0 dan katta bo‘lishi kerak. Masalan: <code>200000</code>";
    public static final String ERR_TOO_LARGE = "❗ Qiymat juda katta (eng ko‘pi 999 999 999 999). Qaytadan kiriting.";
    public static final String ERR_PERCENT_RANGE = "❗ Foiz 0 dan 100 gacha bo‘lishi kerak. Masalan: <code>10</code>";
    public static final String ERR_MARGIN_RANGE = "❗ Marja 0 dan 99,99 gacha bo‘lishi kerak. Masalan: <code>20</code>";
    public static final String ERR_TEXT_LENGTH = "❗ Matn juda uzun: eng ko‘pi %d belgi.";
    public static final String ERR_TEXT_EMPTY = "❗ Matn bo‘sh bo‘lishi mumkin emas.";
    public static final String ERR_SKU_FORMAT =
            "❗ SKU 1–64 belgidan iborat bo‘lishi kerak: harflar, raqamlar va <code>- _ . /</code>. Masalan: <code>FUT-001</code>";
    public static final String ERR_SKU_TAKEN = "❗ Bu SKU sizning boshqa mahsulotingizda ishlatilgan. Boshqa SKU kiriting.";
    public static final String ERR_CONFLICT = "⚠️ Mahsulot boshqa joyda o‘zgartirilgan. Yangilangan holatini qayta ochib tahrirlang.";
    public static final String EXPECT_BUTTON = "Iltimos, yuqoridagi tugmalardan birini tanlang.";

    // ------------------------------------------------ mahsulotlar
    public static final String PRODUCTS_TITLE = "📦 <b>Mahsulotlarim</b> — faol: %d ta";
    public static final String PRODUCTS_ARCHIVE_TITLE = "🗄 <b>Arxivdagi mahsulotlar</b>";
    public static final String PRODUCTS_EMPTY =
            "📦 <b>Mahsulotlarim</b>\n\nKatalogingiz hozircha bo‘sh. Mahsulot qo‘shsangiz, uning tannarxini har safar qayta yozishingiz shart bo‘lmaydi.";
    public static final String PRODUCTS_SEARCH_EMPTY = "🔍 «%s» bo‘yicha mahsulot topilmadi.";
    public static final String PRODUCTS_SEARCH_TITLE = "🔍 «%s» bo‘yicha natijalar:";
    public static final String ASK_PRODUCT_SEARCH = "🔍 Qidirish uchun mahsulot nomi yoki SKU qismini yozing.";
    public static final String BTN_NEW_PRODUCT = "➕ Yangi mahsulot";
    public static final String BTN_SEARCH = "🔍 Qidirish";
    public static final String BTN_ARCHIVE_LIST = "🗄 Arxiv";
    public static final String BTN_ACTIVE_LIST = "📦 Faol ro‘yxat";
    public static final String ASK_PRODUCT_NAME = "📦 Yangi mahsulot nomini yozing (1–120 belgi).";
    public static final String ASK_PRODUCT_COST = "💵 1 dona <b>tannarxi</b> (so‘m):";
    public static final String ASK_PRODUCT_PLANNED = "🏷 Rejalashtirilgan sotish narxi (ixtiyoriy, so‘m):";
    public static final String ASK_PRODUCT_SKU = "🔖 SKU / artikul (ixtiyoriy, 64 belgigacha):";
    public static final String PRODUCT_REVIEW = "🔎 <b>Yangi mahsulotni tekshiring</b>\n\n%s";
    public static final String DUP_NAME_WARNING = "\n\n⚠️ Bu nomli faol mahsulot allaqachon bor. Baribir saqlash mumkin.";
    public static final String PRODUCT_SAVED = "✅ Mahsulot saqlandi.";
    public static final String ARCHIVE_CONFIRM =
            "🗄 <b>%s</b> arxivlansinmi?\nU faol ro‘yxatdan yashiriladi; saqlangan hisoblashlar tarixi o‘zgarmaydi. Keyin tiklash mumkin.";
    public static final String BTN_ARCHIVE = "🗄 Arxivlash";
    public static final String BTN_RESTORE = "♻️ Tiklash";
    public static final String ARCHIVED = "🗄 Mahsulot arxivlandi.";
    public static final String RESTORED = "♻️ Mahsulot tiklandi.";
    public static final String EDIT_WHICH = "✏️ Qaysi maydonni o‘zgartiramiz?";
    public static final String BTN_F_NAME = "Nomi";
    public static final String BTN_F_COST = "Tannarx";
    public static final String BTN_F_PLANNED = "Reja narxi";
    public static final String BTN_F_SKU = "SKU";
    public static final String BTN_CLEAR_PLANNED = "🧹 Reja narxini tozalash";
    public static final String BTN_CLEAR_SKU = "🧹 SKU ni tozalash";
    public static final String FIELD_UPDATED = "✅ O‘zgartirildi.";
    public static final String BTN_LIST = "⬅️ Ro‘yxatga";

    // ------------------------------------------------ tarix
    public static final String HISTORY_TITLE = "📊 <b>Hisoblashlar tarixi</b>%s";
    public static final String HISTORY_EMPTY =
            "📊 <b>Hisoblashlar tarixi</b>\n\nHozircha saqlangan hisoblash yo‘q. Natija tarixga faqat «💾 Saqlash» tugmasini bosganingizda yoziladi.";
    public static final String HISTORY_FILTER_EMPTY = "Tanlangan filtr bo‘yicha yozuv topilmadi.";
    public static final String ASK_HISTORY_SEARCH = "🔍 Mahsulot nomi (hisoblash paytidagi nom) bo‘yicha qidirish uchun matn yozing.";
    public static final String ASK_HISTORY_RANGE =
            "📅 Sana oralig‘ini yozing: <code>KK.OO.YYYY-KK.OO.YYYY</code>\nMasalan: <code>01.11.2026-05.11.2026</code> (ikkala kun ham kiradi, Toshkent vaqti).";
    public static final String ERR_RANGE = "❗ Format noto‘g‘ri. Masalan: <code>01.11.2026-05.11.2026</code>";
    public static final String HISTORY_ARCHIVE_CONFIRM = "🗄 Bu yozuv arxivlansinmi? Hisob ma’lumotlari o‘zgarmaydi, keyin tiklash mumkin.";
    public static final String HISTORY_ARCHIVED = "🗄 Yozuv arxivlandi.";
    public static final String HISTORY_RESTORED = "♻️ Yozuv tiklandi.";
    public static final String BTN_RECALC = "🔁 Qayta hisoblash";
    public static final String RECALC_COST_CHOICE =
            "Katalogdagi tannarx o‘zgargan:\n• Hisoblash paytida: <b>%s so‘m</b>\n• Hozir katalogda: <b>%s so‘m</b>\n\nQaysi biri bilan hisoblaymiz?";
    public static final String BTN_OLD_COST = "Eski tannarx bilan";
    public static final String BTN_NEW_COST = "Hozirgi tannarx bilan";
    public static final String RECALC_NOTE = "🔁 Eski hisob asosida <b>yangi qoralama</b> ochildi. Eski yozuv o‘zgarmaydi.";

    // ------------------------------------------------ sozlamalar
    public static final String SETTINGS =
            "⚙️ <b>Sozlamalar</b>\n\n🏪 Biznes nomi: <b>%s</b>\n🙂 Ism: <b>%s</b>\n🛒 Asosiy kanal: <b>%s</b>\n"
                    + "🌐 Til: o‘zbekcha · Valyuta: UZS · Vaqt: Asia/Tashkent\n⭐ Tarif: <b>%s</b>\n📄 Shartlar: %s";
    public static final String BTN_S_BNAME = "✏️ Biznes nomi";
    public static final String BTN_S_DNAME = "✏️ Ism";
    public static final String BTN_S_CHANNEL = "🛒 Asosiy kanal";
    public static final String BTN_S_TERMS = "📄 Shartlar";
    public static final String BTN_S_DELETE = "🗑 Akkauntni o‘chirish";
    public static final String BTN_S_ACCESS = "🔐 Admin kirish ruxsatlari";
    public static final String ACCESS_NONE = "🔐 Hozir hech bir administratorda ma’lumotlaringizga kirish ruxsati yo‘q.";
    public static final String ACCESS_LIST_TITLE = "🔐 <b>Admin kirish ruxsatlari</b>\nIstalgan payt bekor qilishingiz mumkin.";
    public static final String BTN_CLEAR = "🧹 Tozalash";
    public static final String ASK_S_BNAME = "🏪 Yangi biznes nomini yozing (1–80 belgi) yoki tozalang.";
    public static final String ASK_S_DNAME = "🙂 Yangi ismni yozing (1–60 belgi) yoki tozalang.";
    public static final String CHANNEL_CHOOSE = "🛒 Hozirgi asosiy kanal: <b>%s</b>\nYangisini tanlang:";
    public static final String CHANNEL_CONFIRM =
            "Asosiy kanal: <b>%s</b> → <b>%s</b>\nEski saqlangan hisoblashlar va joriy qoralama o‘zgarmaydi. Tasdiqlaysizmi?";
    public static final String CHANNEL_SAME = "Bu kanal allaqachon tanlangan.";
    public static final String SETTINGS_SAVED = "✅ Saqlandi.";
    public static final String TERMS_INFO = "📄 <b>Xizmat shartlari</b>\nQabul qilingan versiya: <b>%s</b>\nQabul qilingan vaqt: %s\n\n%s";
    /** M-23: nima o'chadi / nima qoladi — haqiqatga mos. %s — DELETE_DATA_PURGED yoki DELETE_DATA_RETAINED. */
    public static final String DELETE_STEP1 =
            "🗑 <b>Akkauntni o‘chirish so‘rovi</b>\n\nSo‘rov yuborilgach:\n• biznes amallari darhol to‘xtatiladi;\n"
                    + "• 30 kunlik kutish davri boshlanadi — bu davrda so‘rovni <b>botning o‘zida</b> bekor qilishingiz mumkin.\n\n"
                    + "<b>30 kundan keyin:</b>\n• akkaunt yopiladi;\n• biznes nomi, ismingiz va tugallanmagan qoralamalar o‘chiriladi;\n"
                    + "• administratorlarga berilgan kirish ruxsatlari bekor qilinadi;\n%s\n"
                    + "• rozilik yozuvlari va xavfsizlik jurnali qonuniy dalil sifatida saqlanadi (tijoriy qiymatlarsiz).\n\nDavom etasizmi?";
    public static final String DELETE_DATA_PURGED =
            "• mahsulotlar va saqlangan hisoblashlar <b>o‘chiriladi</b>, Telegram ID’ingiz akkauntdan ajratiladi.";
    public static final String DELETE_DATA_RETAINED =
            "• mahsulotlar va saqlangan hisoblashlar hozircha <b>saqlanib qoladi</b> (ma’lumotlarni saqlash siyosati yuridik tasdiqlanguncha) "
                    + "va hech kimga ko‘rsatilmaydi. To‘liq o‘chirishni qo‘llab-quvvatlash orqali so‘rashingiz mumkin.";
    public static final String DELETE_STEP2 = "❗ <b>Yakuniy tasdiq.</b> Akkauntni o‘chirish so‘rovini yuboraymi?";
    public static final String BTN_DELETE_CONTINUE = "Davom etish";
    public static final String BTN_DELETE_FINAL = "Ha, so‘rov yuborilsin";
    public static final String ERR_NAME_IS_COMMAND = "Nom «/» bilan boshlanmasin — buyruqlar nom sifatida saqlanmaydi. Boshqa nom yozing.";
    public static final String DELETION_CANCELLED_BY_ADMIN =
            "ℹ️ Akkauntingizni o‘chirish so‘rovi administrator tomonidan bekor qilindi.\nSabab: %s\n\n"
                    + "Agar akkauntni baribir o‘chirmoqchi bo‘lsangiz, /delete buyrug‘i bilan qayta so‘rov yuboring.";
    public static final String DELETE_DONE = "So‘rov qabul qilindi. 30 kunlik kutish davri boshlandi.";

    // ------------------------------------------------ tariflar
    public static final String TARIFF =
            "⭐ <b>Tariflar</b>\n\nJoriy tarif: <b>%s</b>\n%s\n\n<b>Imkoniyatlar:</b>\n%s\n\n%s";
    public static final String TARIFF_NO_PAID =
            "Pullik tariflar hozircha e’lon qilinmagan. Yopiq beta davrida barcha asosiy imkoniyatlar bepul.";
    public static final String TARIFF_UNAVAILABLE = "Tarif ma’lumotini hozir olib bo‘lmadi. Keyinroq urinib ko‘ring.";

    // ------------------------------------------------ yordam
    public static final String HELP =
            "❓ <b>Yordam</b>\n\nSavolni tanlang. Asosiy bo‘limlar pastdagi menyuda.\nBuyruqlar: /menu — bosh menyu, /cancel — jarayonni bekor qilish, /settings — sozlamalar, /delete — akkaunt va ma’lumotlarni o‘chirish.";
    public static final String HELP_CONTEXT = "\n\n💡 Hozirgi qadamga oid savol: pastda birinchi bo‘lib ko‘rsatilgan.";
    public static final String HELP_NO_SUPPORT = "";
    public static final String BTN_FAQ_LIST = "⬅️ Savollar";
    public static final String BTN_BACK_TO_FLOW = "↩️ Jarayonga qaytish";

    public static final String[][] FAQ = {
            {"Foyda qanday hisoblanadi?",
                    "Foyda = sotish narxi − (tannarx + barcha ma’lum xarajatlar). Hammasi 1 dona mahsulot uchun hisoblanadi."},
            {"Marja nima?",
                    "Marja = foyda ÷ sotish narxi × 100%. Bu tannarx ustamasi (markup) emas: «tannarx + 20%» 20% marja degani emas."},
            {"Nega natija «taxminiy»?",
                    "Kamida bitta xarajat «Hozir bilmayman» yoki kiritilmagan. U hisobga qo‘shilmaydi, lekin 0 deb ham hisoblanmaydi — shuning uchun natija taxminiy."},
            {"Komissiya qanday kiritiladi?",
                    "Foiz (sotish narxidan) yoki 1 dona uchun summa sifatida. Komissiya faqat bir marta ayiriladi."},
            {"Narx qaysi usullarda topiladi?",
                    "3 usul: maqsadli foyda summasi, maqsadli marja va zararsizlik nuqtasi. Natija «kamida» shu maqsadga yetadigan narx."},
            {"Nega narx yuqoriga yaxlitlanadi?",
                    "Tavsiya narxi 1, 100 yoki 1 000 so‘m qadamda yuqoriga yaxlitlanadi va foyda shu narxda qayta tekshiriladi — maqsaddan pastga tushmasligi uchun."},
            {"Natija tarixda yo‘q?",
                    "Tarixga faqat «💾 Saqlash» bosilgan natijalar yoziladi. Shunchaki hisoblash yoki ko‘rish tarix yaratmaydi."},
            {"Eski hisobni tahrirlay olamanmi?",
                    "Yo‘q, saqlangan hisob o‘zgarmaydi. «🔁 Qayta hisoblash» orqali uning asosida yangi qoralama ochiladi."},
            {"Tannarx o‘zgarsa, eski hisob nima bo‘ladi?",
                    "Eski saqlangan hisob o‘sha paytdagi qiymatlar bilan qoladi. Katalogdagi o‘zgarish uni yangilamaydi."},
            {"Qoralama qancha saqlanadi?",
                    "Tugallanmagan hisoblash qoralamasi oxirgi kiritishdan keyin 24 soat saqlanadi. Saqlangan tarix va mahsulotlar o‘chmaydi."},
            {"Uzum stavkalari avtomatik olinadimi?",
                    "Yo‘q. Hozirgi versiyada marketplace’lar bilan avtomatik ulanish yo‘q — komissiya va boshqa qiymatlarni o‘zingiz kiritasiz."},
            {"Telefon raqamim kerakmi?",
                    "Yo‘q. SellerFlow’dan foydalanish uchun telefon, pasport yoki boshqa hujjat talab qilinmaydi."}
    };
}
