package uz.sellerflow.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.common.Errors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-5: Superadmin dashboard — faqat agregat sonlar (tijoriy qiymatlar yo'q).
 * Kunlar Asia/Tashkent bo'yicha. Oldingi davr bilan solishtirish: bir xil uzunlikdagi oldingi oraliq.
 * 7 kunlik retention: davrda ro'yxatdan o'tgan va kamida 7 kun oldin kelgan foydalanuvchilardan
 * ro'yxatdan o'tgandan 7 va undan ortiq kun keyin botda faol bo'lganlari ulushi.
 */
@Service
public class DashboardService {

    static final ZoneId TZ = ZoneId.of("Asia/Tashkent");
    private static final String DAY = "(%s at time zone 'Asia/Tashkent')::date";

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(int period) {
        if (period != 7 && period != 30 && period != 90) throw new Errors.Validation("PERIOD_INVALID");
        // 13-noyabr LOW: og'ir so'rov bot bilan umumiy pool'ni uzoq band qilmasin
        jdbc.execute("set local statement_timeout = '5s'");
        LocalDate today = LocalDate.now(TZ);
        LocalDate from = today.minusDays(period - 1L);
        LocalDate prevFrom = from.minusDays(period);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("newSellers", kpi(
                count("select count(*) from users where created_at >= ? and created_at < ?", ts(from), ts(today.plusDays(1))),
                count("select count(*) from users where created_at >= ? and created_at < ?", ts(prevFrom), ts(from))));
        kpis.put("activeSellers", kpi(
                count("select count(distinct user_id) from user_activity_days where day between ? and ?", from, today),
                count("select count(distinct user_id) from user_activity_days where day between ? and ?", prevFrom, from.minusDays(1))));
        kpis.put("savedCalculations", kpi(
                count("select count(*) from calculation_history where saved_at >= ? and saved_at < ?", ts(from), ts(today.plusDays(1))),
                count("select count(*) from calculation_history where saved_at >= ? and saved_at < ?", ts(prevFrom), ts(from))));
        kpis.put("activeProducts", Map.of("value",
                count("select count(*) from products where status = 'ACTIVE'")));
        kpis.put("totalSellers", Map.of("value",
                count("select count(*) from users where status = 'ACTIVE'")));

        // 13-noyabr LOW: kun uchun korrelyatsiyali subquery (har kun to'liq skan) o'rniga davr bo'yicha bitta GROUP BY
        Map<LocalDate, long[]> byDay = new java.util.TreeMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) byDay.put(d, new long[3]);
        java.sql.Timestamp fromTs = java.sql.Timestamp.from(from.atStartOfDay(TZ).toInstant());
        jdbc.query("select " + day("created_at") + ", count(*) from users where created_at >= ? group by 1",
                rs -> { long[] v = byDay.get(rs.getDate(1).toLocalDate()); if (v != null) v[0] = rs.getLong(2); }, fromTs);
        jdbc.query("select day, count(*) from user_activity_days where day >= ? group by 1",
                rs -> { long[] v = byDay.get(rs.getDate(1).toLocalDate()); if (v != null) v[1] = rs.getLong(2); }, from);
        jdbc.query("select " + day("saved_at") + ", count(*) from calculation_history where saved_at >= ? group by 1",
                rs -> { long[] v = byDay.get(rs.getDate(1).toLocalDate()); if (v != null) v[2] = rs.getLong(2); }, fromTs);
        List<Map<String, Object>> seriesOut = new ArrayList<>();
        byDay.forEach((d, v) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("day", d.toString());
            x.put("newSellers", v[0]);
            x.put("activeSellers", v[1]);
            x.put("savedCalculations", v[2]);
            seriesOut.add(x);
        });

        List<Map<String, Object>> channels = jdbc.queryForList("""
                select b.primary_channel as "channel", count(*) as "count" from businesses b
                join users u on u.id = b.owner_user_id where u.status <> 'CLOSED' group by 1 order by 2 desc, 1""");

        // Onboarding voronkasi: davrda ro'yxatdan o'tganlar qaysi bosqichgacha yetgan
        String cohort = "from users u where u.created_at >= ? and u.created_at < ?"; // indeksdan foydalanadi
        List<Map<String, Object>> funnel = List.of(
                step("STARTED", count("select count(*) " + cohort, ts(from), ts(today.plusDays(1)))),
                step("TERMS_ACCEPTED", count("select count(*) " + cohort
                        + " and exists (select 1 from consents c where c.user_id = u.id)", ts(from), ts(today.plusDays(1)))),
                step("BUSINESS_CREATED", count("select count(*) " + cohort
                        + " and exists (select 1 from businesses b where b.owner_user_id = u.id)", ts(from), ts(today.plusDays(1)))),
                step("FIRST_PRODUCT", count("select count(*) " + cohort
                        + " and exists (select 1 from businesses b join products p on p.business_id = b.id where b.owner_user_id = u.id)",
                        ts(from), ts(today.plusDays(1)))),
                step("FIRST_CALCULATION", count("select count(*) " + cohort
                        + " and exists (select 1 from calculation_history h where h.user_id = u.id)", ts(from), ts(today.plusDays(1)))));

        LocalDate cohortTo = today.minusDays(7);
        long eligible = 0;
        long retained = 0;
        if (!cohortTo.isBefore(from)) {
            eligible = count("select count(*) " + cohort, ts(from), ts(cohortTo.plusDays(1)));
            retained = count("select count(*) " + cohort + " and exists (select 1 from user_activity_days a where a.user_id = u.id and a.day >= "
                    + day("u.created_at") + " + 7)", ts(from), ts(cohortTo.plusDays(1)));
        }
        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("cohort", eligible);
        retention.put("retained", retained);
        retention.put("pct", eligible == 0 ? null : pct(retained, eligible));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("from", from.toString());
        out.put("to", today.toString());
        out.put("kpis", kpis);
        out.put("series", seriesOut);
        out.put("channels", channels);
        out.put("funnel", funnel);
        out.put("retention7d", retention);
        return out;
    }

    private static java.sql.Timestamp ts(LocalDate d) {
        return java.sql.Timestamp.from(d.atStartOfDay(TZ).toInstant());
    }

    private static String day(String col) {
        return DAY.formatted(col);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private static Map<String, Object> step(String name, long count) {
        return Map.of("step", name, "count", count);
    }

    static Map<String, Object> kpi(long value, long prev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("previous", prev);
        m.put("changePct", prev == 0 ? null : pct(value - prev, prev));
        return m;
    }

    static BigDecimal pct(long part, long whole) {
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }
}
