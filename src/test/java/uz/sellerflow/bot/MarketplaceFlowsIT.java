package uz.sellerflow.bot;

import org.junit.jupiter.api.Test;
import uz.sellerflow.support.FakeUzum;
import uz.sellerflow.support.IntegrationTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** V2: Uzum ulanishi, sinxron, tannarx tez kiritish, fayl importi, foyda yo'qotishlari, «Agar…». */
class MarketplaceFlowsIT extends IntegrationTest {

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long biz(long u) {
        return jdbc.queryForObject("select b.id from businesses b join users us on us.id = b.owner_user_id where us.telegram_user_id = ?",
                Long.class, u);
    }

    private static final String TWO_PRODUCTS = """
            [{"productId": 77, "title": "Futbolka", "status": {"value": "IN_STOCK"},
              "skuList": [
                {"skuId": 1, "skuTitle": "Oq", "sellerItemCode": "FT-W", "price": 100000, "commission": 20, "quantityActive": 5},
                {"skuId": 2, "skuTitle": "Qora", "sellerItemCode": "FT-B", "price": 50000, "purchasePrice": 48000, "commission": 20}
              ]}]""";

    @Test
    void connect_tokenMessageDeleted_encryptedAtRest_syncImports_thenDisconnect() {
        long u = onboardedUser();
        uzum.setProducts(9001, TWO_PRODUCTS);
        text(u, Texts.BTN_PRODUCTS);
        click(u, Texts.BTN_IMPORT);
        click(u, Texts.BTN_IMPORT_UZUM);
        assertThat(lastText(u)).contains("API-ключи");
        click(u, Texts.BTN_UZUM_ENTER_KEY);

        text(u, "noto'g'ri-kalit-123456");
        assertThat(tg.deleted).hasSize(1);
        assertThat(lastText(u)).contains("Kalit noto‘g‘ri");
        assertThat(count("select count(*) from marketplace_connections")).isZero();

        click(u, Texts.BTN_UZUM_ENTER_KEY);
        text(u, FakeUzum.VALID);
        assertThat(tg.deleted).hasSize(2);
        assertThat(tg.forChat(u).stream().anyMatch(m -> m.text().contains("Uzum ulandi"))).isTrue();
        assertThat(lastText(u)).contains("Uzum'dan yangilandi").contains("SKU: 2").contains("1 ta mahsulotda tannarx yo‘q");
        // kalit hech qayerda ochiq holda yo'q: chat xabarlari, sessiya, ulanish jadvali
        assertThat(tg.forChat(u).stream().noneMatch(m -> m.text().contains(FakeUzum.VALID))).isTrue();
        assertThat(count("select count(*) from telegram_flows where data::text like ?", "%" + FakeUzum.VALID + "%")).isZero();
        byte[] cipher = jdbc.queryForObject("select token_ciphertext from marketplace_connections", byte[].class);
        assertThat(new String(cipher, StandardCharsets.ISO_8859_1)).doesNotContain("uzum_test_key");
        assertThat(jdbc.queryForObject("select token_hint from marketplace_connections", String.class)).isEqualTo("…6789");

        long b = biz(u);
        assertThat(count("select count(*) from products where business_id = ? and external_source = 'UZUM'", b)).isEqualTo(2);
        assertThat(count("select count(*) from products where business_id = ? and sku = 'FT-B' and unit_cost = 48000 and commission_pct = 20", b))
                .isEqualTo(1);

        // ikkinchi sinxron dublikat yaratmaydi, tannarx (sotuvchi kiritgani) saqlanadi
        jdbc.update("update marketplace_connections set last_sync_at = now() - interval '1 hour'");
        jdbc.update("update products set unit_cost = 30000 where sku = 'FT-W'");
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_SYNC);
        assertThat(count("select count(*) from products where business_id = ?", b)).isEqualTo(2);
        assertThat(count("select count(*) from products where sku = 'FT-W' and unit_cost = 30000")).isEqualTo(1);
        // darhol qayta — 10 daqiqalik cheklov
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_SYNC);
        assertThat(lastText(u)).contains("10 daqiqada bir marta");

        click(u, Texts.BTN_UZUM_DISCONNECT);
        click(u, Texts.BTN_UZUM_DISCONNECT_YES);
        assertThat(count("select count(*) from marketplace_connections")).isZero();
        assertThat(count("select count(*) from products where business_id = ?", b)).as("mahsulotlar qoladi").isEqualTo(2);
    }

    @Test
    void missingCost_quickEntry_thenCalcFromUzumProduct_usesCommission_andLeakReport() {
        long u = onboardedUser();
        uzum.setProducts(9001, TWO_PRODUCTS);
        text(u, Texts.BTN_SETTINGS);
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_ENTER_KEY);
        text(u, FakeUzum.VALID);

        click(u, "Tannarxni kiritish");
        assertThat(lastText(u)).contains("Futbolka — Oq").contains("Uzum komissiyasi: 20%");
        text(u, "abc");
        assertThat(lastText(u)).contains("raqam");
        text(u, "60 000");
        assertThat(lastText(u)).contains("Barcha mahsulotlarda tannarx bor");
        assertThat(count("select count(*) from products where sku = 'FT-W' and unit_cost = 60000")).isEqualTo(1);

        // Leak report: FT-W: 100000 − 60000 − 20% = 20000 (20%); FT-B: 50000 − 48000 − 10000 = −8000 (zarar)
        text(u, Texts.BTN_PRODUCTS);
        click(u, Texts.BTN_LEAKS);
        String leaks = lastText(u);
        assertThat(leaks).contains("Tahlil qilindi: 2 ta").contains("zarar: <b>1</b>").contains("🔻 Futbolka — Qora")
                .contains("foyda <b>-8 000").contains("Kanal stavkalari saqlanmagan");

        // Mahsulotdan foyda hisobi: komissiya so'ralmaydi (Uzum'dan), kanal — Uzum
        long pid = jdbc.queryForObject("select id from products where sku = 'FT-W'", Long.class);
        callback(u, "p:v:" + pid);
        assertThat(lastText(u)).contains("🟣 <b>Uzum</b>").contains("Komissiya: 20%");
        click(u, "Foyda");
        if (tg.findButton(u, Texts.BTN_SWITCH_YES).isPresent()) click(u, Texts.BTN_SWITCH_YES);
        assertThat(lastText(u)).as("komissiya qadami o'tkazildi — logistika so'raladi").contains("ogistika");
        click(u, "Yo‘q (0)");
        click(u, Texts.BTN_EXTRAS_ZERO);
        click(u, "Hisoblash");
        assertThat(lastText(u)).contains("Komissiya (20%): 20 000").contains("20 000 so‘m");
        assertThat(tg.findButton(u, "Stavkalarni «Uzum")).as("kanal — Uzum").isPresent();

        click(u, Texts.BTN_WHATIF);
        String w = lastText(u);
        assertThat(w).contains("Agar… simulyatori").contains("Narx -10%").contains("Komissiya +2 p.p. (22%)")
                .contains("Chegirma 10%");
        // narx −10% = 90 000: 90000 − 60000 − 18000 = 12 000 (−8 000)
        assertThat(w).contains("<b>12 000</b> (−8 000)");
    }

    @Test
    void fileImport_csvCreatesAndUpdatesBySku() {
        long u = onboardedUser();
        String csv = "Nomi;Artikul;Tannarx;Narx\nChoynak;CH-1;30000;59000\nPiyola;PY-1;;12000\n";
        document(u, "mahsulotlar.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(lastText(u)).contains("Import tugadi").contains("yangi: 2").contains("Tannarx ← «Tannarx»");
        long b = biz(u);
        assertThat(count("select count(*) from products where business_id = ?", b)).isEqualTo(2);
        assertThat(count("select count(*) from products where business_id = ? and unit_cost is null", b)).isEqualTo(1);

        document(u, "yangi.csv", "Nomi;Artikul;Tannarx\nChoynak katta;CH-1;35000\n".getBytes(StandardCharsets.UTF_8));
        assertThat(lastText(u)).contains("yangilangan: 1");
        assertThat(count("select count(*) from products where sku = 'CH-1' and unit_cost = 35000 and planned_sale_price = 59000")).isEqualTo(1);

        document(u, "rasm.png", new byte[]{1, 2, 3});
        assertThat(lastText(u)).contains("Faqat .xlsx yoki .csv");
    }

    @Test
    void accountPurge_removesConnection() {
        long u = onboardedUser();
        text(u, Texts.BTN_SETTINGS);
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_ENTER_KEY);
        text(u, FakeUzum.VALID);
        assertThat(count("select count(*) from marketplace_connections")).isEqualTo(1);
        long userId = jdbc.queryForObject("select id from users where telegram_user_id = ?", Long.class, u);
        purger.clearActivity(userId);
        assertThat(count("select count(*) from marketplace_connections")).isZero();
    }

    @Test
    void periodReport_fromUzumFinance_netProfit_transit_bestWorst() {
        long u = onboardedUser();
        uzum.setProducts(9001, TWO_PRODUCTS);
        long t = System.currentTimeMillis() - 2L * 24 * 3600 * 1000;
        uzum.setFinance(9001, """
                [{"id": 1, "orderId": 100, "productId": 77, "skuId": 1, "skuTitle": "Oq", "sellPrice": 100000, "amount": 2,
                  "commission": 40000, "logisticDeliveryFee": 10000, "status": "TO_WITHDRAW", "date": %d},
                 {"id": 2, "orderId": 101, "skuId": 2, "sellPrice": 50000, "amount": 1, "commission": 10000,
                  "logisticDeliveryFee": 5000, "status": "TO_WITHDRAW", "date": %d},
                 {"id": 3, "orderId": 102, "skuId": 1, "sellPrice": 100000, "amount": 1, "commission": 20000,
                  "logisticDeliveryFee": 5000, "status": "PROCESSING", "date": %d},
                 {"id": 4, "orderId": 103, "skuId": 2, "sellPrice": 50000, "amount": 1, "status": "CANCELED", "date": %d}]
                """.formatted(t, t, t, t), """
                [{"id": 10, "code": "С000003", "name": "Хранение", "paymentPrice": -3000, "type": "OUTCOME", "status": "CONFIRMED", "dateService": %d},
                 {"id": 11, "code": "FBS003", "name": "Штраф", "paymentPrice": 2000, "type": "OUTCOME", "status": "CONFIRMED", "dateService": %d},
                 {"id": 12, "code": "BALANCE_CORRECTION", "paymentPrice": 50000, "type": "OUTCOME", "dateService": %d},
                 {"id": 13, "code": "Y000119", "paymentPrice": 4000, "type": "OUTCOME", "status": "CANCELED", "dateService": %d}]
                """.formatted(t, t, t, t));
        text(u, Texts.BTN_SETTINGS);
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_ENTER_KEY);
        text(u, FakeUzum.VALID);
        assertThat(lastText(u)).contains("Sotuv qatorlari: 4 · xarajatlar: 4");
        assertThat(count("select count(*) from marketplace_expenses where expense_id = 10 and category = 'STORAGE' and amount = 3000")).isEqualTo(1);
        jdbc.update("update products set unit_cost = 60000 where sku = 'FT-W'");

        click(u, Texts.BTN_REPORT);
        String r = lastText(u);
        assertThat(r).contains("Davr hisoboti").contains("oxirgi 30 kun")
                .contains("Buyurtmalar: <b>2</b> · sotilgan: <b>3 dona</b>")
                .contains("💵 Tushum: <b>250 000</b>").contains("− Uzum komissiyasi: 50 000").contains("− Logistika: 15 000")
                .contains("= Uzum to‘lovi: <b>185 000</b>").contains("− Tannarx: 168 000")
                .contains("− Uzum xarajatlari: 5 000").contains("Saqlash: 3 000").contains("Jarimalar: 2 000")
                .contains("Sof foyda: 12 000 so‘m</b> · marja 4,8%")
                .contains("Yo‘ldagi pul:</b> 1 dona").contains("75 000")
                .contains("Bekor qilingan qatorlar: 1")
                .contains("Futbolka — Oq — 2 dona, 30 000").contains("Futbolka — Qora — 1 dona, -13 000")
                .contains("Soliq hisobga olinmadi");
        click(u, "7 kun");
        assertThat(lastText(u)).contains("oxirgi 7 kun").contains("250 000");
        click(u, "O‘tgan oy");
        assertThat(lastText(u)).contains("o‘tgan oy");

        // takroriy sinxron — qatorlar dublikat bo'lmaydi
        jdbc.update("update marketplace_connections set last_sync_at = now() - interval '1 hour'");
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_SYNC);
        assertThat(count("select count(*) from marketplace_sales")).isEqualTo(4);
    }

    @Test
    void financeFailure_doesNotBreakProductSync() {
        long u = onboardedUser();
        uzum.setProducts(9001, TWO_PRODUCTS);
        uzum.financeFails = true;
        text(u, Texts.BTN_SETTINGS);
        click(u, Texts.BTN_IMPORT_UZUM);
        click(u, Texts.BTN_UZUM_ENTER_KEY);
        text(u, FakeUzum.VALID);
        assertThat(lastText(u)).contains("SKU: 2").contains("Moliya ma’lumotlari (sotuvlar) hozir o‘qilmadi");
        assertThat(count("select count(*) from products")).isEqualTo(2);
        click(u, Texts.BTN_IMPORT_UZUM);
        assertThat(lastText(u)).contains("Moliya ma’lumotlari o‘qilmadi");
    }

    @org.springframework.beans.factory.annotation.Autowired uz.sellerflow.account.AccountPurger purger;
}
