package uz.sellerflow.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.Csv;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * V1+ (TZ 5-modul): sotuvchi o'z ma'lumotini CSV qilib yuklab oladi — mahsulotlar va saqlangan hisoblashlar.
 * Faqat o'z {@code business_id} si. Formula-injection himoyasi, UTF-8 BOM. 10 daqiqada bir marta.
 */
@Service
public class DataExportService {

    public static final Duration COOLDOWN = Duration.ofMinutes(10);
    static final int MAX_ROWS = 10_000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Tashkent"));

    public record Export(byte[] products, int productRows, byte[] history, int historyRows) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public DataExportService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    /** Oxirgi eksportdan COOLDOWN o'tmagan bo'lsa — keyingi ruxsat vaqti. */
    public Optional<Instant> nextAllowedAt(long businessId) {
        java.sql.Timestamp last = jdbc.query("""
                select max(created_at) from audit_events where business_id = ? and action = 'USER_DATA_EXPORTED'
                  and created_at > now() - make_interval(mins => ?)""",
                rs -> rs.next() ? rs.getTimestamp(1) : null, businessId, (int) COOLDOWN.toMinutes());
        return last == null ? Optional.empty() : Optional.of(last.toInstant().plus(COOLDOWN));
    }

    @Transactional
    public Export export(long businessId) {
        StringBuilder p = new StringBuilder(Csv.BOM).append(Csv.row("id", "nomi", "sku", "tannarx", "reja_narxi", "holat", "yaratilgan"));
        int[] pc = {0};
        jdbc.query("""
                select id, name, sku, unit_cost, planned_sale_price, status, created_at from products
                where business_id = ? order by id limit ?""", rs -> {
            pc[0]++;
            p.append(Csv.row(String.valueOf(rs.getLong(1)), Csv.cell(rs.getString(2)), Csv.cell(rs.getString(3)),
                    Csv.number(plain(rs.getBigDecimal(4))), Csv.number(plain(rs.getBigDecimal(5))), Csv.cell(rs.getString(6)),
                    Csv.cell(TS.format(rs.getTimestamp(7).toInstant()))));
        }, businessId, MAX_ROWS);

        StringBuilder h = new StringBuilder(Csv.BOM).append(Csv.row("id", "saqlangan", "turi", "mahsulot", "kanal",
                "tannarx", "sotish_narxi", "chegirma", "samarali_narx", "jami_xarajat", "foyda", "marja_foiz",
                "maqsad", "tavsiya_narxi", "aniqlik", "engine_versiya", "arxivda"));
        int[] hc = {0};
        jdbc.query("""
                select id, saved_at, calculation_type, product_name_snapshot, channel_snapshot, input_snapshot::text,
                       output_snapshot::text, accuracy_status, calculation_version, visibility_status
                from calculation_history where business_id = ? order by id limit ?""", rs -> {
            hc[0]++;
            JsonNode in = read(rs.getString(6));
            JsonNode out = read(rs.getString(7));
            boolean profit = "PROFIT".equals(rs.getString(3));
            h.append(Csv.row(String.valueOf(rs.getLong(1)), Csv.cell(TS.format(rs.getTimestamp(2).toInstant())),
                    Csv.cell(profit ? "foyda" : "narx"), Csv.cell(rs.getString(4)), Csv.cell(rs.getString(5)),
                    Csv.number(txt(in, "cost")),
                    Csv.number(profit ? txt(out, "salePrice") : txt(out, "recommendedPrice")),
                    Csv.number(profit ? txt(out, "discount") : txt(out, "discountAtPrice")),
                    Csv.number(txt(out, "effectivePrice")),
                    Csv.number(profit ? txt(out, "knownExpensesTotal") : txt(out, "knownExpensesAtPrice")),
                    Csv.number(profit ? txt(out, "profit") : txt(out, "profitAtPrice")),
                    Csv.number(profit ? txt(out, "marginPct") : txt(out, "marginAtPrice")),
                    Csv.cell(profit ? "" : txt(out, "goal")),
                    Csv.number(profit ? "" : txt(out, "recommendedPrice")),
                    Csv.cell(rs.getString(8)), Csv.cell(rs.getString(9)),
                    Csv.cell("ARCHIVED".equals(rs.getString(10)) ? "ha" : "yo'q")));
        }, businessId, MAX_ROWS);

        audit.record(Actor.USER, null, businessId, "USER_DATA_EXPORTED", "BUSINESS", businessId,
                "products=" + pc[0] + ", history=" + hc[0]);
        return new Export(p.toString().getBytes(StandardCharsets.UTF_8), pc[0],
                h.toString().getBytes(StandardCharsets.UTF_8), hc[0]);
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String txt(JsonNode n, String key) {
        return n.hasNonNull(key) ? n.get(key).asText() : "";
    }
}
