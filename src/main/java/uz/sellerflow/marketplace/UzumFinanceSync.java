package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.common.TextUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * V3: Uzum moliya — sotuv qatorlari ({@code /v1/finance/orders}, group=false) va xarajatlar ({@code /v1/finance/expenses}).
 * Birinchi marta 90 kunlik tarix, keyin har sinxronda oxirgi 35 kun qayta o'qiladi (PROCESSING → TO_WITHDRAW o'zgarishlari).
 * Qator kaliti — (do'kon, buyurtma, qator ID); xarajat — ID. Xarajat tasnifi {@code code} bo'yicha (Kiril/lotin bir xil).
 */
@Component
public class UzumFinanceSync {

    static final int ORDERS_PAGE = 1000;
    static final int EXPENSES_PAGE = 1000;
    static final int MAX_PAGES = 40;
    static final Duration FIRST = Duration.ofDays(90);
    static final Duration REFRESH = Duration.ofDays(35);

    public record Result(int sales, int expenses) {}

    private final JdbcTemplate jdbc;
    private final UzumClient client;

    public UzumFinanceSync(JdbcTemplate jdbc, UzumClient client) {
        this.jdbc = jdbc;
        this.client = client;
    }

    public Result sync(long businessId, String token, List<Long> shopIds) {
        Timestamp last = jdbc.query("select finance_synced_at from marketplace_connections where business_id = ? and marketplace = 'UZUM'",
                rs -> rs.next() ? rs.getTimestamp(1) : null, businessId);
        Instant now = Instant.now();
        long from = now.minus(last == null ? FIRST : REFRESH).toEpochMilli();
        long to = now.toEpochMilli();
        int sales = 0, expenses = 0;
        for (long shop : shopIds) {
            for (int page = 0; page < MAX_PAGES; page++) {
                var pg = client.financeOrders(token, List.of(shop), from, to, page, ORDERS_PAGE);
                for (JsonNode r : pg.products()) if (saveSale(businessId, shop, r)) sales++;
                if (pg.products().size() < ORDERS_PAGE) break;
            }
            for (int page = 0; page < MAX_PAGES; page++) {
                var pg = client.financeExpenses(token, List.of(shop), from, to, page, EXPENSES_PAGE);
                for (JsonNode r : pg.products()) if (saveExpense(businessId, shop, r)) expenses++;
                if (pg.products().size() < EXPENSES_PAGE) break;
            }
        }
        jdbc.update("update marketplace_connections set finance_synced_at = now(), finance_error = null where business_id = ? and marketplace = 'UZUM'",
                businessId);
        return new Result(sales, expenses);
    }

    boolean saveSale(long businessId, long shopId, JsonNode r) {
        long lineId = r.path("id").asLong(0);
        long orderId = r.path("orderId").asLong(0);
        Instant at = instant(r, "date", "dateCreated", "orderDate");
        if (lineId == 0 || orderId == 0 || at == null) return false;
        String title = TextUtil.truncate(text(r, "skuTitle", "productTitle", "title"), 200);
        String status = text(r, "status");
        if (status == null) status = r.path("cancelled").asBoolean(false) ? "CANCELED" : "PROCESSING";
        jdbc.update("""
                insert into marketplace_sales(business_id, marketplace, shop_id, order_id, line_id, product_id, sku_id, title,
                  sell_price, amount, commission, logistic_fee, purchase_price, status, sold_at)
                values (?, 'UZUM', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (business_id, marketplace, shop_id, order_id, line_id) do update set
                  product_id = excluded.product_id, sku_id = excluded.sku_id, title = excluded.title,
                  sell_price = excluded.sell_price, amount = excluded.amount, commission = excluded.commission,
                  logistic_fee = excluded.logistic_fee, purchase_price = excluded.purchase_price, status = excluded.status,
                  sold_at = excluded.sold_at, synced_at = now()""",
                businessId, shopId, orderId, lineId, nullableLong(r, "productId"), nullableLong(r, "skuId"), title,
                money(r.path("sellPrice")), Math.max(0, r.path("amount").asInt(0)), money(r.path("commission")).abs(),
                money(r.path("logisticDeliveryFee")).abs(), positiveOrNull(r.path("purchasePrice")),
                TextUtil.truncate(status, 24), Timestamp.from(at));
        return true;
    }

    boolean saveExpense(long businessId, long shopId, JsonNode r) {
        long id = r.path("id").asLong(0);
        Instant at = instant(r, "dateService", "dateCreated", "dateUpdated");
        if (id == 0 || at == null) return false;
        String code = text(r, "code");
        String name = text(r, "name");
        String source = text(r, "source");
        String type = "INCOME".equalsIgnoreCase(text(r, "type")) ? "INCOME" : "OUTCOME";
        jdbc.update("""
                insert into marketplace_expenses(business_id, marketplace, expense_id, shop_id, code, name, source, category,
                  amount, direction, status, service_date)
                values (?, 'UZUM', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (business_id, marketplace, expense_id) do update set code = excluded.code, name = excluded.name,
                  source = excluded.source, category = excluded.category, amount = excluded.amount,
                  direction = excluded.direction, status = excluded.status, service_date = excluded.service_date, synced_at = now()""",
                businessId, id, shopId, TextUtil.truncate(code, 40), TextUtil.truncate(name, 200), TextUtil.truncate(source, 80),
                classify(code, name), money(r.path("paymentPrice")).abs(), type, TextUtil.truncate(text(r, "status"), 16),
                Timestamp.from(at));
        return true;
    }

    /** Xarajat kodi → toifa. Kodning birinchi harfi Kiril (С, У) yoki lotin (C, Y) bo'lishi mumkin. */
    static String classify(String code, String name) {
        String c = code == null ? "" : code.strip().toUpperCase(Locale.ROOT).replace('С', 'C').replace('У', 'Y');
        switch (c) {
            case "C000003", "C000005" -> { return "STORAGE"; }
            case "C000004" -> { return "RETURN_STORAGE"; }
            case "C000007", "00001", "FBS001", "FBS002", "FBS003", "FBS004", "DBS003" -> { return "FINE"; }
            case "Y000119", "Y000120", "Y000050" -> { return "ADS"; }
            case "Y000002", "Y000003" -> { return "PHOTO"; }
            case "C000010", "C000011" -> { return "WITHDRAW_FEE"; }
            case "BALANCE_CORRECTION" -> { return "TRANSFER"; }
            default -> { }
        }
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("штраф") || n.contains("jarima")) return "FINE";
        if (n.contains("реклам") || n.contains("reklama") || n.contains("буст")) return "ADS";
        if (n.contains("возврат") && n.contains("хранен")) return "RETURN_STORAGE";
        if (n.contains("хранен") || n.contains("saqlash")) return "STORAGE";
        if (n.contains("фото") || n.contains("съемк") || n.contains("съёмк")) return "PHOTO";
        if (n.contains("вывод") || n.contains("yechish")) return "WITHDRAW_FEE";
        return "OTHER";
    }

    static Instant instant(JsonNode r, String... keys) {
        for (String k : keys) {
            JsonNode n = r.path(k);
            if (n.isMissingNode() || n.isNull()) continue;
            if (n.isNumber()) {
                long v = n.asLong();
                return v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
            }
            String s = n.asText("").strip();
            if (s.isEmpty()) continue;
            if (s.matches("\\d{10,13}")) return instant(new com.fasterxml.jackson.databind.node.LongNode(Long.parseLong(s)));
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException e) {
                try {
                    return java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.of("Asia/Tashkent")).toInstant();
                } catch (DateTimeParseException ignored) {
                    // keyingi kalit
                }
            }
        }
        return null;
    }

    private static Instant instant(com.fasterxml.jackson.databind.node.LongNode n) {
        long v = n.asLong();
        return v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
    }

    private static String text(JsonNode r, String... keys) {
        for (String k : keys) {
            String v = r.path(k).asText(null);
            if (v != null && !v.isBlank() && !"null".equals(v)) return v.strip();
        }
        return null;
    }

    private static Long nullableLong(JsonNode r, String k) {
        long v = r.path(k).asLong(0);
        return v == 0 ? null : v;
    }

    private static BigDecimal money(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        try {
            BigDecimal b = new BigDecimal(n.asText());
            return b.abs().compareTo(uz.sellerflow.calculation.Money.MAX) > 0 ? BigDecimal.ZERO : b;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal positiveOrNull(JsonNode n) {
        BigDecimal b = money(n);
        return b.signum() > 0 ? b : null;
    }
}
