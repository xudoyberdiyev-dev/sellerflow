package uz.sellerflow.bot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.sellerflow.catalog.ProductImportService;
import uz.sellerflow.catalog.ProductImportService.Row;
import uz.sellerflow.marketplace.PeriodReportService;
import uz.sellerflow.support.FakeUzum;
import uz.sellerflow.support.IntegrationTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Production readiness: V2–V3 ma'lumotlari (Uzum, import, hisobot) bo'yicha biznes izolyatsiyasi va hisobot izchilligi. */
class TenantIsolationIT extends IntegrationTest {

    @Autowired PeriodReportService reports;
    @Autowired ProductImportService importer;

    private long biz(long u) {
        return jdbc.queryForObject("select b.id from businesses b join users us on us.id = b.owner_user_id where us.telegram_user_id = ?",
                Long.class, u);
    }

    private void sale(long biz, long line, String price, int amount, String status) {
        jdbc.update("""
                insert into marketplace_sales(business_id, shop_id, order_id, line_id, sku_id, title, sell_price, amount,
                  commission, logistic_fee, status, sold_at) values (?, 1, ?, ?, 5, 'X', ?, ?, 0, 0, ?, now() - interval '1 day')""",
                biz, line, line, new BigDecimal(price), amount, status);
    }

    @Test
    void reports_importAndCostFlow_neverCrossBusinesses() {
        long ua = onboardedUser(), ub = onboardedUser();
        long a = biz(ua), b = biz(ub);
        sale(a, 1, "100000", 1, "TO_WITHDRAW");
        sale(b, 2, "999000", 3, "TO_WITHDRAW");
        jdbc.update("insert into marketplace_expenses(business_id, expense_id, category, amount, direction, service_date) "
                + "values (?, 77, 'FINE', 50000, 'OUTCOME', now() - interval '1 day')", b);

        Instant from = Instant.now().minus(Duration.ofDays(30)), to = Instant.now();
        var ra = reports.report(a, from, to);
        assertThat(ra.revenue()).isEqualByComparingTo("100000");
        assertThat(ra.units()).isEqualTo(1);
        assertThat(ra.operatingTotal()).isEqualByComparingTo("0");
        assertThat(reports.report(b, from, to).revenue()).isEqualByComparingTo("2997000");

        // Bir xil SKU ikki biznesda — import faqat o'z biznesidagi mahsulotni yangilaydi
        importer.upsert(a, ProductImportService.Source.FILE, List.of(new Row(null, null, null, "A mahsulot", "SAME-1", null,
                new BigDecimal("10"), null, null, null, null, null)));
        importer.upsert(b, ProductImportService.Source.FILE, List.of(new Row(null, null, null, "B mahsulot", "SAME-1", null,
                null, new BigDecimal("500"), null, null, null, null)));
        assertThat(jdbc.queryForObject("select count(*) from products where sku = 'SAME-1'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select unit_cost from products where business_id = ? and sku = 'SAME-1'", BigDecimal.class, a))
                .isEqualByComparingTo("10");

        // B ning tannarxi yo'q mahsulotiga A tugma orqali yeta olmaydi
        long bProduct = jdbc.queryForObject("select id from products where business_id = ?", Long.class, b);
        callback(ua, "mk:cs:" + bProduct);
        assertThat(lastText(ua)).contains("eskirgan");
        text(ua, Texts.BTN_PRODUCTS);
        callback(ua, "p:v:" + bProduct);
        assertThat(lastText(ua)).doesNotContain("B mahsulot");

        // Hisobot: A botida B summalari ko'rinmaydi
        callback(ua, "mk:rep:30");
        assertThat(lastText(ua)).contains("100 000").doesNotContain("2 997 000");
    }

    @Test
    void report_isDeterministic_andHandlesZeroAndLargeValues() {
        long u = onboardedUser();
        long a = biz(u);
        Instant from = Instant.now().minus(Duration.ofDays(30)), to = Instant.now();
        var empty = reports.report(a, from, to);
        assertThat(empty.empty()).isTrue();
        assertThat(empty.marginPct()).isEqualByComparingTo("0");

        sale(a, 1, "999999999999", 1000, "TO_WITHDRAW");   // juda katta qiymat — BigDecimal, toshib ketmaydi
        sale(a, 2, "0", 5, "TO_WITHDRAW");                 // nol narx
        sale(a, 3, "50000", 0, "TO_WITHDRAW");             // nol soni — hisobga olinmaydi
        var r1 = reports.report(a, from, to);
        var r2 = reports.report(a, from, to);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.revenue()).isEqualByComparingTo("999999999999000");
        assertThat(r1.units()).isEqualTo(1005);
        assertThat(r1.partial()).as("tannarx va soliq yo'q — taxminiy").isTrue();
        callback(u, "mk:rep:30");
        assertThat(lastText(u)).contains("999 999 999 999 000");
    }

    @Test
    void uzumToken_ofOneBusiness_isNotUsableByAnother() {
        long ua = onboardedUser(), ub = onboardedUser();
        text(ua, Texts.BTN_SETTINGS);
        click(ua, Texts.BTN_IMPORT_UZUM);
        click(ua, Texts.BTN_UZUM_ENTER_KEY);
        text(ua, FakeUzum.VALID);
        // B ning shifrmatnini A nikiga ko'chirsak ham, AAD (biznes ID) mos kelmaydi — ochilmaydi
        jdbc.update("insert into marketplace_connections(business_id, marketplace, token_ciphertext, token_hint, shop_ids) "
                + "select ?, 'UZUM', token_ciphertext, token_hint, shop_ids from marketplace_connections where business_id = ?", biz(ub), biz(ua));
        text(ub, Texts.BTN_SETTINGS);
        click(ub, Texts.BTN_IMPORT_UZUM);
        click(ub, Texts.BTN_UZUM_SYNC);
        assertThat(tg.forChat(ub).stream().anyMatch(m -> m.text().contains("Uzum hozir javob bermayapti"))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from products where business_id = ?", Long.class, biz(ub))).isZero();
    }
}
