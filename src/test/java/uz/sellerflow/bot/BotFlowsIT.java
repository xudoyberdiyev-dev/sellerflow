package uz.sellerflow.bot;

import org.junit.jupiter.api.Test;
import uz.sellerflow.support.IntegrationTest;
import uz.sellerflow.telegram.Tg;

import static org.assertj.core.api.Assertions.assertThat;

/** Telegram bot E2E ssenariylari (soxta Telegram + haqiqiy PostgreSQL). */
class BotFlowsIT extends IntegrationTest {

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ================================================================ onboarding

    @Test
    void onboarding_createsSingleActiveBusinessWithFree_andRepeatStartDoesNotDuplicate() {
        long u = newUserId();
        text(u, "/start");
        assertThat(lastText(u)).contains("xush kelibsiz");
        click(u, "Boshlash");
        assertThat(lastText(u)).contains("Xizmat shartlari");
        click(u, "Roziman");
        click(u, "Wildberries");
        text(u, "Ali savdo");
        text(u, "Ali");
        assertThat(lastText(u)).contains("Wildberries").contains("Ali savdo").contains("Free (Beta)");
        click(u, "Faollashtirish");
        click(u, "Faollashtirish"); // eski tugma — takroriy faollashtirish
        text(u, "/start");

        assertThat(count("select count(*) from businesses b join users us on us.id = b.owner_user_id where us.telegram_user_id = ?", u)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select plan_code from businesses b join users us on us.id = b.owner_user_id where us.telegram_user_id = ?", String.class, u)).isEqualTo("FREE");
        assertThat(count("select count(*) from consents c join users us on us.id = c.user_id where us.telegram_user_id = ?", u)).isEqualTo(1);
        // asosiy menyu Reply Keyboard
        assertThat(tg.last(u).markup()).isInstanceOf(Tg.ReplyKeyboard.class);
    }

    @Test
    void consentDeclined_accountStaysOnboarding() {
        long u = newUserId();
        text(u, "/start");
        click(u, "Boshlash");
        click(u, "Rozi emasman");
        assertThat(lastText(u)).contains("Rozilik berilmasa");
        text(u, Texts.BTN_PROFIT);
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("ONBOARDING");
        assertThat(count("select count(*) from businesses")).isZero();
    }

    // ================================================================ profit

    private void fullProfitInput(long u) {
        text(u, Texts.BTN_PROFIT);
        text(u, "120 000");
        text(u, "200000");
        click(u, "Foiz");
        text(u, "10");
        click(u, "Summa");
        text(u, "5000");
        click(u, "Qadoqlash"); click(u, "Summa"); text(u, "3000");
        click(u, "Reklama"); click(u, "Summa"); text(u, "7000");
        click(u, "Soliq"); click(u, "Summa"); text(u, "4000");
        click(u, "Xizmat"); click(u, "Yo‘q (0)");
        click(u, "Boshqa"); click(u, "Summa"); text(u, "1000");
        click(u, "Davom etish");
    }

    @Test
    void profit_approvedExample_completeInput_savedOnceOnly() {
        long u = onboardedUser();
        fullProfitInput(u);
        assertThat(lastText(u)).contains("Ma’lumotlarni tekshiring");
        click(u, "Hisoblash");
        String result = lastText(u);
        assertThat(result).contains("40 000").contains("20%").contains("160 000").contains("Barcha xarajatlar kiritilgan");
        assertThat(count("select count(*) from calculation_history")).as("natija avtomatik saqlanmaydi").isZero();

        String saveData = tg.findButton(u, "Saqlash").orElseThrow().callbackData();
        callback(u, saveData);
        assertThat(lastText(u)).contains("saqlandi");
        callback(u, saveData); // takroriy callback
        assertThat(count("select count(*) from calculation_history")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select accuracy_status from calculation_history", String.class)).isEqualTo("COMPLETE_INPUT");
        assertThat(jdbc.queryForObject("select calculation_version from calculation_history", String.class)).isEqualTo("1.0.0");
    }

    @Test
    void profit_skippedExtras_arePartialEstimate_notZero() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "160000");
        text(u, "200000");
        click(u, "Yo‘q (0)");
        click(u, "Hozir bilmayman");
        click(u, "Davom etish");
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("40 000").contains("Taxminiy natija").contains("Logistika").contains("Qadoqlash");
    }

    @Test
    void validation_firstErrorExplainsWithExample() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "abc");
        assertThat(lastText(u)).contains("Son tushunilmadi").contains("120000");
        text(u, "100");
        text(u, "0");
        assertThat(lastText(u)).contains("0 dan katta");
    }

    @Test
    void editFromReview_returnsToReview_andRecalculatesPercent() {
        long u = onboardedUser();
        fullProfitInput(u);
        click(u, "Sotish narxi");
        text(u, "300000");
        assertThat(lastText(u)).contains("Ma’lumotlarni tekshiring").contains("300 000");
        click(u, "Hisoblash");
        // komissiya 10% * 300 000 = 30 000; jami = 120 000+30 000+5 000+3 000+7 000+4 000+1 000 = 170 000
        assertThat(lastText(u)).contains("30 000").contains("130 000");
    }

    // ================================================================ price

    @Test
    void price_targetProfit_defaultRounding1000_thenStep1() {
        long u = onboardedUser();
        text(u, Texts.BTN_PRICE);
        click(u, "Maqsadli foyda summasi");
        text(u, "125000");
        click(u, "Foiz");
        text(u, "10");
        click(u, "Summa");
        text(u, "5000");
        click(u, "Qolganlari yo‘q (0)");
        text(u, "25000");
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("173 000").contains("25 700").contains("172 222,22");
        click(u, "✓ 1 000"); // joriy qadam belgilangan
        click(u, "1");
        assertThat(lastText(u)).contains("172 223");
        click(u, "Saqlash");
        assertThat(count("select count(*) from calculation_history where calculation_type = 'PRICE'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select output_snapshot->>'recommendedPrice' from calculation_history", String.class))
                .isEqualTo("172223");
    }

    @Test
    void price_impossibleMargin_showsNoSolution_withoutSaveButton() {
        long u = onboardedUser();
        text(u, Texts.BTN_PRICE);
        click(u, "Maqsadli marja");
        text(u, "1000");
        click(u, "Foiz");
        text(u, "50");
        click(u, "Yo‘q (0)");
        click(u, "Qolganlari yo‘q (0)");
        text(u, "60");
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("hech qanday musbat narx");
        assertThat(tg.last(u).buttons()).noneMatch(b -> b.text().contains("Saqlash"));
    }

    // ================================================================ drafts & navigation

    @Test
    void draft_resumePrompt_andViewingOtherSectionsDoesNotLoseDraft() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "50000");
        text(u, Texts.BTN_PRODUCTS); // faqat ko'rish — tasdiq so'ralmaydi
        assertThat(lastText(u)).contains("Mahsulotlarim");
        text(u, Texts.BTN_PROFIT);
        assertThat(lastText(u)).contains("tugallanmagan");
        click(u, "Davom ettirish");
        assertThat(lastText(u)).contains("sotish narxini");
        text(u, "80000");
        assertThat(lastText(u)).contains("komissiyasi");
    }

    @Test
    void startingAnotherInputFlow_withUnsavedDraft_asksConfirmation() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "50000");
        text(u, Texts.BTN_PRODUCTS);
        click(u, "Yangi mahsulot");
        assertThat(lastText(u)).contains("saqlanmagan ma’lumot");
        click(u, "Qoralamaga qaytish");
        assertThat(lastText(u)).contains("sotish narxini");
        text(u, Texts.BTN_PRODUCTS);
        click(u, "Yangi mahsulot");
        click(u, "Tashlab, davom etish");
        assertThat(lastText(u)).contains("Yangi mahsulot nomini");
    }

    @Test
    void staleCallback_fromOldRevision_isRejectedSafely() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "100");
        text(u, "200");
        String oldCommission = tg.findButton(u, "Foiz").orElseThrow().callbackData();
        text(u, "/cancel");
        callback(u, oldCommission);
        assertThat(lastText(u)).contains("eskirgan");
    }

    @Test
    void expiredDraft_isNotResumed() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "100");
        jdbc.update("update telegram_flows set expires_at = now() - interval '1 minute'");
        text(u, Texts.BTN_PROFIT);
        assertThat(tg.forChat(u).stream().anyMatch(m -> m.text().contains("muddati tugadi"))).isTrue();
        assertThat(lastText(u)).contains("tannarxini");
    }

    @Test
    void helpDuringWizard_isContextual_andReturnsToSameStep() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "100");
        text(u, "200");
        String before = jdbc.queryForObject("select expires_at::text from telegram_flows", String.class);
        text(u, "/help");
        assertThat(lastText(u)).contains("Yordam");
        assertThat(tg.findButton(u, "Komissiya qanday")).isPresent();
        assertThat(jdbc.queryForObject("select expires_at::text from telegram_flows", String.class)).isEqualTo(before);
        click(u, "Jarayonga qaytish");
        assertThat(lastText(u)).contains("komissiyasi");
    }

    @Test
    void duplicateUpdateId_isProcessedOnce() {
        long u = onboardedUser();
        Tg.User from = new Tg.User(u, false, "T", null, null);
        Tg.Update up = new Tg.Update(42, new Tg.Message(5, from, new Tg.Chat(u, "private"), "/menu"), null);
        dispatcher.handle(up);
        dispatcher.handle(up);
        assertThat(tg.forChat(u)).hasSize(1);
    }

    /** M-10 regression: vaqtinchalik baza xatosida update "qayta ishlangan" deb belgilanmaydi va qayta keladi. */
    @Test
    void transientFailure_updateNotMarkedProcessed_andRetrySucceeds() {
        long u = onboardedUser();
        tg.clear();
        Tg.User from = new Tg.User(u, false, "T", null, null);
        Tg.Update up = new Tg.Update(4242, new Tg.Message(5, from, new Tg.Chat(u, "private"), "/menu"), null);
        tg.failNextSend(new org.springframework.dao.TransientDataAccessResourceException("db down"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.handle(up))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        assertThat(count("select count(*) from processed_updates where update_id = 4242")).isZero();
        dispatcher.handle(up); // Telegram qayta yuboradi
        assertThat(tg.forChat(u)).hasSize(1);
        assertThat(count("select count(*) from processed_updates where update_id = 4242")).isEqualTo(1);
        dispatcher.handle(up); // endi dublikat
        assertThat(tg.forChat(u)).hasSize(1);
    }

    /** Oddiy (biznes/dastur) xato — update belgilanadi, foydalanuvchiga xato xabari, qayta ishlanmaydi. */
    @Test
    void nonTransientFailure_isMarkedProcessed() {
        long u = onboardedUser();
        tg.clear();
        Tg.User from = new Tg.User(u, false, "T", null, null);
        Tg.Update up = new Tg.Update(4343, new Tg.Message(5, from, new Tg.Chat(u, "private"), "/menu"), null);
        tg.failNextSend(new IllegalStateException("bug"));
        dispatcher.handle(up);
        assertThat(count("select count(*) from processed_updates where update_id = 4343")).isEqualTo(1);
    }

    /** M-14 LOW: soxta callback data — istisno tashqariga chiqmaydi, foydalanuvchi xato xabarini oladi. */
    @Test
    void malformedCallbackData_isHandledGracefully() {
        long u = onboardedUser();
        tg.clear();
        callback(u, "g:not-a-number:a");
        callback(u, "PPROFIT");
        assertThat(tg.forChat(u)).isNotEmpty();
    }

    /** M-26: onboardingda «Ortga» va ma'lumotni o'chirish (tugma va /delete). */
    @Test
    void onboarding_backNavigation_andWipeBeforeActivation() {
        long u = newUserId();
        text(u, "/start");
        click(u, "Boshlash");
        click(u, "Roziman");
        assertThat(lastText(u)).isNotBlank();
        click(u, "Uzum Market");          // -> biznes nomi
        click(u, "Ortga");                // -> kanal
        assertThat(tg.last(u).buttons()).anyMatch(b -> b.text().equals("Uzum Market"));
        click(u, "Ortga");                // -> xush kelibsiz
        assertThat(tg.last(u).buttons()).anyMatch(b -> b.text().contains("ma’lumotni o‘chirish"));

        text(u, "/delete");
        assertThat(lastText(u)).contains("Ro‘yxatdan o‘tishni bekor qilish");
        click(u, "Ha, o‘chirilsin");
        assertThat(lastText(u)).contains("o‘chirildi");
        assertThat(count("select count(*) from users where telegram_user_id = " + u)).isZero();
        assertThat(count("select count(*) from users where status = 'CLOSED' and telegram_user_id < 0")).isEqualTo(1);
        assertThat(count("select count(*) from consents")).isEqualTo(1); // dalil sifatida, shaxssiz
        assertThat(count("select count(*) from audit_events where action = 'ONBOARDING_DATA_DELETED'")).isEqualTo(1);

        text(u, "/start"); // yangidan
        assertThat(count("select count(*) from users where telegram_user_id = " + u + " and status = 'ONBOARDING'")).isEqualTo(1);
    }

    @Test
    void groupChat_noBusinessFlow() {
        long u = newUserId();
        Tg.User from = new Tg.User(u, false, "T", null, null);
        dispatcher.handle(new Tg.Update(77, new Tg.Message(5, from, new Tg.Chat(-100123, "supergroup"), "/start"), null));
        assertThat(tg.last(-100123).text()).contains("shaxsiy chatda");
        assertThat(count("select count(*) from users")).isZero();
    }

    // ================================================================ catalog & history

    private long createProduct(long u, String name, String cost) {
        text(u, Texts.BTN_PRODUCTS);
        click(u, "Yangi mahsulot");
        text(u, name);
        text(u, cost);
        click(u, "O‘tkazib yuborish");
        text(u, "SKU-" + name.hashCode());
        String save = tg.findButton(u, "Saqlash").orElseThrow().callbackData();
        callback(u, save);
        callback(u, save); // takroriy — dublikat yo'q
        return jdbc.queryForObject("select id from products where name = ?", Long.class, name);
    }

    @Test
    void product_create_idempotent_calcFromCatalog_snapshotImmutableAfterCostEdit() {
        long u = onboardedUser();
        long pid = createProduct(u, "Futbolka", "120000");
        assertThat(count("select count(*) from products")).isEqualTo(1);

        callback(u, "p:calc:" + pid);
        assertThat(lastText(u)).contains("sotish narxini");
        text(u, "200000");
        click(u, "Yo‘q (0)");
        click(u, "Yo‘q (0)");
        click(u, "Qolganlari yo‘q (0)");
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("80 000");
        click(u, "Saqlash");

        // katalog tannarxini o'zgartirish
        callback(u, "p:e:" + pid);
        click(u, "Tannarx");
        text(u, "140000");
        assertThat(lastText(u)).contains("140 000");
        assertThat(jdbc.queryForObject("select input_snapshot->>'cost' from calculation_history", String.class)).isEqualTo("120000.00");

        // qayta hisoblash: eski / hozirgi tannarx tanlovi
        long hid = jdbc.queryForObject("select id from calculation_history", Long.class);
        callback(u, "h:rc:" + hid);
        assertThat(lastText(u)).contains("120 000").contains("140 000");
        click(u, "Hozirgi tannarx bilan");
        assertThat(lastText(u)).contains("140 000");
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("60 000");
        click(u, "Saqlash");
        assertThat(count("select count(*) from calculation_history")).isEqualTo(2);
        assertThat(count("select count(*) from calculation_history where source_history_id = ?", hid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select output_snapshot->>'profit' from calculation_history where id = ?", String.class, hid))
                .isEqualTo("80000.00");
    }

    @Test
    void product_archive_restore_andSkuUniqueness() {
        long u = onboardedUser();
        long pid = createProduct(u, "Krossovka", "300000");
        callback(u, "p:a:" + pid);
        click(u, "Arxivlash");
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, pid)).isEqualTo("ARCHIVED");
        callback(u, "p:r:" + pid);
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, pid)).isEqualTo("ACTIVE");
        assertThat(count("select count(*) from products")).isEqualTo(1);

        text(u, Texts.BTN_PRODUCTS);
        click(u, "Yangi mahsulot");
        text(u, "Boshqa");
        text(u, "1");
        click(u, "O‘tkazib yuborish");
        text(u, "SKU-" + "Krossovka".hashCode());
        assertThat(lastText(u)).contains("SKU sizning boshqa mahsulotingizda");
    }

    @Test
    void tenantIsolation_otherUsersProductAndHistoryAreInvisible() {
        long a = onboardedUser();
        long pid = createProduct(a, "Maxfiy", "999");
        long b = onboardedUser();
        callback(b, "p:v:" + pid);
        assertThat(lastText(b)).contains("topilmadi");
        callback(b, "p:a:" + pid);
        callback(b, "p:ac:" + pid);
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, pid)).isEqualTo("ACTIVE");
        assertThat(tg.forChat(b).stream().noneMatch(m -> m.text().contains("Maxfiy"))).isTrue();
    }

    @Test
    void history_filterAndSearch_bySnapshotName() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "100");
        text(u, "200");
        click(u, "Yo‘q (0)");
        click(u, "Yo‘q (0)");
        click(u, "Qolganlari yo‘q (0)");
        click(u, "Nom");
        text(u, "Choynak");
        click(u, "Hisoblash");
        click(u, "Saqlash");

        text(u, Texts.BTN_HISTORY);
        assertThat(tg.findButton(u, "Choynak")).isPresent();
        click(u, "🏷 Narx");
        assertThat(lastText(u)).contains("topilmadi");
        click(u, "Hammasi");
        click(u, "Qidirish");
        text(u, "choy");
        assertThat(tg.findButton(u, "Choynak")).isPresent();
        click(u, "Choynak");
        assertThat(lastText(u)).contains("Foyda hisobi").contains("100");
    }

    // ================================================================ settings & account

    @Test
    void settings_channelChange_doesNotTouchHistorySnapshot_andDeletionIsTwoStep() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "100");
        text(u, "200");
        click(u, "Yo‘q (0)");
        click(u, "Yo‘q (0)");
        click(u, "Qolganlari yo‘q (0)");
        click(u, "Hisoblash");
        click(u, "Saqlash");

        text(u, Texts.BTN_SETTINGS);
        click(u, "Asosiy kanal");
        click(u, "Instagram");
        assertThat(lastText(u)).contains("Uzum Market").contains("Instagram");
        click(u, "Tasdiqlash");
        assertThat(jdbc.queryForObject("select primary_channel from businesses", String.class)).isEqualTo("INSTAGRAM");
        assertThat(jdbc.queryForObject("select channel_snapshot from calculation_history", String.class)).isEqualTo("UZUM");

        click(u, "Biznes nomi");
        text(u, "Yangi do‘kon");
        assertThat(jdbc.queryForObject("select business_name from businesses", String.class)).isEqualTo("Yangi do‘kon");

        click(u, "Akkauntni o‘chirish");
        click(u, "Davom etish");
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("ACTIVE");
        click(u, "Ha, so‘rov yuborilsin");
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("DELETION_REQUESTED");
        text(u, Texts.BTN_PROFIT);
        var msgs = tg.forChat(u);
        assertThat(msgs.get(msgs.size() - 2).text()).contains("o‘chirish so‘rovingiz");
        assertThat(msgs.get(msgs.size() - 1).buttons()).anyMatch(b -> b.text().contains("bekor qilish")); // M-25
    }

    @Test
    void tariffs_showOnlyServerConfiguredFree_noBuyButton() {
        long u = onboardedUser();
        text(u, Texts.BTN_TARIFFS);
        assertThat(lastText(u)).contains("Free (Beta)").contains("cheklanmagan").contains("e’lon qilinmagan");
        assertThat(tg.last(u).buttons()).noneMatch(b -> b.text().toLowerCase().contains("sotib") || b.text().contains("To‘lash"));
    }

    @Test
    void suspendedAccount_blocksBusinessActions_butHelpWorks() {
        long u = onboardedUser();
        jdbc.update("update users set status = 'SUSPENDED' where telegram_user_id = ?", u);
        text(u, Texts.BTN_PROFIT);
        assertThat(lastText(u)).contains("cheklangan");
        text(u, "/help");
        assertThat(lastText(u)).contains("Yordam");
    }
}
