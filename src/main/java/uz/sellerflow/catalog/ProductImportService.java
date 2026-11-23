package uz.sellerflow.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.calculation.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

/**
 * V2: mahsulotlarni ommaviy qo'shish/yangilash — Uzum sinxroni va fayl importi uchun umumiy.
 * Moslash tartibi: tashqi SKU ID → SKU (katta-kichik harfsiz) → (faqat fayl) aynan shu nom → yangi mahsulot.
 * Hamma so'rovlar {@code business_id} bilan.
 */
@Service
public class ProductImportService {

    public static final int MAX_ROWS = 5000;
    private static final Pattern SKU_PATTERN = Pattern.compile("^[\\p{L}\\p{N}._\\-/]{1,64}$");

    public enum Source { UZUM, FILE }

    /** Bitta qator. Bo'sh maydon (null) — «ma'lumot yo'q», mavjud qiymat o'zgarmaydi. */
    public record Row(Long externalSkuId, Long externalProductId, Long shopId, String name, String sku, String barcode,
                      BigDecimal unitCost, BigDecimal price, BigDecimal commissionPct,
                      Integer stock, Integer sold, Integer returned) {}

    public record Result(int created, int updated, int skipped) {
        public int total() { return created + updated; }
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ProductImportService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public Result upsert(long businessId, Source source, List<Row> rows) {
        int created = 0, updated = 0, skipped = 0;
        int n = 0;
        for (Row r : rows) {
            if (++n > MAX_ROWS) {
                skipped += rows.size() - MAX_ROWS;
                break;
            }
            String name = cleanName(r.name());
            BigDecimal cost = money(r.unitCost(), true);
            BigDecimal price = money(r.price(), false);
            String sku = cleanSku(r.sku());
            String barcode = trim(r.barcode(), 64);
            BigDecimal commission = r.commissionPct() == null || r.commissionPct().signum() < 0
                    || r.commissionPct().compareTo(BigDecimal.valueOf(100)) > 0 ? null
                    : r.commissionPct().setScale(2, RoundingMode.HALF_UP);

            Long id = null;
            if (r.externalSkuId() != null) {
                id = first("select id from products where business_id = ? and external_source = ? and external_sku_id = ?",
                        businessId, source.name(), r.externalSkuId());
            }
            if (id == null && sku != null) {
                id = first("select id from products where business_id = ? and lower(sku) = lower(?)", businessId, sku);
            }
            if (id == null && source == Source.FILE && name != null && sku == null) {
                id = first("select id from products where business_id = ? and status = 'ACTIVE' and name = ? order by id limit 1",
                        businessId, name);
            }
            if (id == null) {
                if (name == null || (source == Source.FILE && cost == null && price == null)) {
                    skipped++;
                    continue;
                }
                jdbc.update("""
                        insert into products(business_id, name, unit_cost, planned_sale_price, sku, external_source,
                          external_product_id, external_sku_id, external_shop_id, barcode, commission_pct, stock_qty,
                          sold_qty, returned_qty, synced_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, case when ? then now() end)""",
                        businessId, name, cost, price, sku, r.externalSkuId() == null ? (source == Source.FILE ? "FILE" : null) : source.name(),
                        r.externalProductId(), r.externalSkuId(), r.shopId(), barcode, commission, r.stock(), r.sold(), r.returned(),
                        source == Source.UZUM);
                created++;
            } else {
                // Uzum tannarxni faqat bo'sh bo'lsa to'ldiradi; fayl — sotuvchining ongli kiritishi, ustidan yozadi
                String costSql = source == Source.UZUM ? "unit_cost = coalesce(unit_cost, ?)" : "unit_cost = coalesce(?, unit_cost)";
                jdbc.update("update products set " + costSql + """
                        , planned_sale_price = coalesce(?, planned_sale_price),
                          external_source = coalesce(?, external_source),
                          external_product_id = coalesce(?, external_product_id),
                          external_sku_id = coalesce(?, external_sku_id),
                          external_shop_id = coalesce(?, external_shop_id),
                          barcode = coalesce(?, barcode),
                          commission_pct = coalesce(?, commission_pct),
                          stock_qty = coalesce(?, stock_qty),
                          sold_qty = coalesce(?, sold_qty),
                          returned_qty = coalesce(?, returned_qty),
                          synced_at = case when ? then now() else synced_at end,
                          version = version + 1, updated_at = now()
                        where id = ? and business_id = ?""",
                        cost, price, r.externalSkuId() == null ? null : source.name(), r.externalProductId(),
                        r.externalSkuId(), r.shopId(), barcode, commission, r.stock(), r.sold(), r.returned(),
                        source == Source.UZUM, id, businessId);
                updated++;
            }
        }
        audit.record(source == Source.UZUM ? Actor.SYSTEM : Actor.USER, null, businessId, "PRODUCTS_IMPORTED", "BUSINESS",
                businessId, "source=" + source + ", created=" + created + ", updated=" + updated + ", skipped=" + skipped);
        return new Result(created, updated, skipped);
    }

    private Long first(String sql, Object... args) {
        List<Long> l = jdbc.query(sql, (rs, i) -> rs.getLong(1), args);
        return l.isEmpty() ? null : l.get(0);
    }

    static String cleanName(String raw) {
        if (raw == null) return null;
        String t = raw.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;
        if (t.codePointCount(0, t.length()) > 120) t = t.substring(0, t.offsetByCodePoints(0, 119)) + "…";
        return t;
    }

    static String cleanSku(String raw) {
        if (raw == null) return null;
        String t = raw.strip();
        return SKU_PATTERN.matcher(t).matches() ? t : null;
    }

    private static String trim(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Pul qiymati: manfiy yoki chegaradan katta — e'tiborsiz; narx uchun 0 ham e'tiborsiz. */
    private static BigDecimal money(BigDecimal v, boolean allowZero) {
        if (v == null) return null;
        if (v.signum() < 0 || (!allowZero && v.signum() == 0) || v.compareTo(Money.MAX) > 0) return null;
        return Money.scale(v);
    }
}
