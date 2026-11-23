package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.channel.ChannelRateService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * V3 (TZ 3.1 chuqur, 3.5, 3.8): davr bo'yicha haqiqiy foyda — Uzum sotuv qatorlari va xarajatlaridan.
 * <pre>
 * tushum     = Σ sell_price × amount                 (faqat TO_WITHDRAW va PARTIALLY_CANCELLED qoldig'i)
 * to'lov     = tushum − komissiya − logistika        (Uzum o'tkazadigan summa)
 * tannarx    = Σ C × amount                          (C — sotuvchi kiritgani, bo'lmasa qatordagi purchasePrice)
 * operatsion = saqlash + qaytim saqlash + jarima + reklama + fotostudiya + yechish haqi + boshqa (OUTCOME, bekor qilinmagan)
 * soliq, qadoqlash, boshqa = Uzum kanal stavkalaridan (bo'lsa)
 * sof foyda  = to'lov − tannarx − operatsion − soliq − qadoqlash − boshqa
 * </pre>
 * Logistika operatsion xarajatga qo'shilmaydi (sotuv qatorida ayrilgan). Ichki o'tkazma (TRANSFER) va INCOME — hisobga olinmaydi.
 */
@Service
public class PeriodReportService {

    public enum Category { STORAGE, RETURN_STORAGE, FINE, ADS, PHOTO, WITHDRAW_FEE, OTHER }

    public record ProductLine(String title, int units, BigDecimal revenue, BigDecimal contribution, boolean costKnown) {}

    public record Report(Instant from, Instant to, int orders, int units, BigDecimal revenue, BigDecimal commission,
                         BigDecimal logistics, BigDecimal payout, BigDecimal cogs, int unitsWithoutCost,
                         Map<Category, BigDecimal> operating, BigDecimal operatingTotal, BigDecimal tax, boolean taxKnown,
                         BigDecimal packaging, BigDecimal netProfit, BigDecimal marginPct,
                         int inTransitUnits, BigDecimal inTransitPayout, int canceledLines,
                         List<ProductLine> best, List<ProductLine> worst, int productsWithoutCost, boolean partial) {
        public boolean empty() { return units == 0 && inTransitUnits == 0 && operatingTotal.signum() == 0; }
    }

    private final JdbcTemplate jdbc;
    private final ChannelRateService rates;

    public PeriodReportService(JdbcTemplate jdbc, ChannelRateService rates) {
        this.jdbc = jdbc;
        this.rates = rates;
    }

    public boolean hasData(long businessId) {
        Boolean b = jdbc.queryForObject("select exists(select 1 from marketplace_sales where business_id = ?) "
                + "or exists(select 1 from marketplace_expenses where business_id = ?)", Boolean.class, businessId, businessId);
        return Boolean.TRUE.equals(b);
    }

    public Report report(long businessId, Instant from, Instant to) {
        Timestamp f = Timestamp.from(from), t = Timestamp.from(to);
        // Sotilganlar — mahsulot bo'yicha (SKU yoki nom), tannarx: sotuvchi kiritgani → qatordagi purchasePrice
        List<Object[]> rows = jdbc.query("""
                select coalesce(p.name, s.title, '—') as title, s.sku_id,
                       sum(s.amount) as units, sum(s.sell_price * s.amount) as revenue,
                       sum(s.commission) as commission, sum(s.logistic_fee) as logistics,
                       sum(case when coalesce(p.unit_cost, s.purchase_price) is not null
                                then coalesce(p.unit_cost, s.purchase_price) * s.amount else 0 end) as cogs,
                       sum(case when coalesce(p.unit_cost, s.purchase_price) is null then s.amount else 0 end) as no_cost_units,
                       count(distinct s.order_id) as orders
                from marketplace_sales s
                left join products p on p.business_id = s.business_id and p.external_source = 'UZUM' and p.external_sku_id = s.sku_id
                where s.business_id = ? and s.sold_at >= ? and s.sold_at < ? and s.status in ('TO_WITHDRAW', 'PARTIALLY_CANCELLED')
                  and s.amount > 0
                group by coalesce(p.name, s.title, '—'), s.sku_id""",
                (rs, i) -> new Object[]{rs.getString(1), rs.getInt(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getInt(8), rs.getInt(9)},
                businessId, f, t);
        int units = 0, noCost = 0, productsNoCost = 0;
        BigDecimal revenue = BigDecimal.ZERO, commission = BigDecimal.ZERO, logistics = BigDecimal.ZERO, cogs = BigDecimal.ZERO;
        List<ProductLine> lines = new ArrayList<>();
        for (Object[] r : rows) {
            int u = (Integer) r[1];
            BigDecimal rev = (BigDecimal) r[2], com = (BigDecimal) r[3], log = (BigDecimal) r[4], cg = (BigDecimal) r[5];
            int nc = (Integer) r[6];
            units += u;
            noCost += nc;
            revenue = revenue.add(rev);
            commission = commission.add(com);
            logistics = logistics.add(log);
            cogs = cogs.add(cg);
            boolean known = nc == 0;
            if (!known) productsNoCost++;
            lines.add(new ProductLine((String) r[0], u, Money.scale(rev), Money.scale(rev.subtract(com).subtract(log).subtract(cg)), known));
        }
        Integer orders = jdbc.queryForObject("""
                select count(distinct (shop_id, order_id)) from marketplace_sales where business_id = ? and sold_at >= ? and sold_at < ?
                  and status in ('TO_WITHDRAW', 'PARTIALLY_CANCELLED') and amount > 0""", Integer.class, businessId, f, t);
        BigDecimal payout = revenue.subtract(commission).subtract(logistics);

        Map<Category, BigDecimal> op = new EnumMap<>(Category.class);
        jdbc.query("""
                select category, sum(amount) from marketplace_expenses
                where business_id = ? and service_date >= ? and service_date < ? and direction = 'OUTCOME'
                  and category <> 'TRANSFER' and coalesce(status, '') not in ('CANCELED', 'REFUNDED')
                group by category""", rs -> {
            try {
                op.put(Category.valueOf(rs.getString(1)), Money.scale(rs.getBigDecimal(2)));
            } catch (IllegalArgumentException ignored) {
                op.merge(Category.OTHER, Money.scale(rs.getBigDecimal(2)), BigDecimal::add);
            }
        }, businessId, f, t);
        BigDecimal opTotal = op.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Soliq va qadoqlash — sotuvchining Uzum kanal stavkalaridan (Uzum ularni bermaydi)
        BigDecimal tax = BigDecimal.ZERO, pack = BigDecimal.ZERO;
        boolean taxKnown = false;
        var saved = rates.get(businessId, SalesChannel.UZUM).orElse(null);
        if (saved != null) {
            JsonNode tx = saved.get("T");
            if (tx != null && "KNOWN".equals(tx.path("s").asText())) {
                BigDecimal v = new BigDecimal(tx.path("v").asText("0"));
                tax = "PCT".equals(tx.path("t").asText()) ? revenue.multiply(v).divide(Money.HUNDRED, 2, RoundingMode.HALF_UP)
                        : v.multiply(BigDecimal.valueOf(units));
                taxKnown = true;
            } else if (tx != null && "ZERO".equals(tx.path("s").asText())) {
                taxKnown = true;
            }
            for (String code : new String[]{"Q", "O"}) {
                JsonNode x = saved.get(code);
                if (x != null && "KNOWN".equals(x.path("s").asText()) && "FIX".equals(x.path("t").asText())) {
                    pack = pack.add(new BigDecimal(x.path("v").asText("0")).multiply(BigDecimal.valueOf(units)));
                }
            }
        }
        BigDecimal net = payout.subtract(cogs).subtract(opTotal).subtract(tax).subtract(pack);
        BigDecimal margin = revenue.signum() == 0 ? BigDecimal.ZERO : net.multiply(Money.HUNDRED).divide(revenue, 2, RoundingMode.HALF_UP);

        Object[] transit = jdbc.queryForObject("""
                select coalesce(sum(amount), 0), coalesce(sum(sell_price * amount - commission - logistic_fee), 0)
                from marketplace_sales where business_id = ? and sold_at >= ? and sold_at < ? and status = 'PROCESSING'""",
                (rs, i) -> new Object[]{rs.getInt(1), rs.getBigDecimal(2)}, businessId, f, t);
        Integer canceled = jdbc.queryForObject("""
                select count(*) from marketplace_sales where business_id = ? and sold_at >= ? and sold_at < ? and status = 'CANCELED'""",
                Integer.class, businessId, f, t);

        List<ProductLine> known = lines.stream().filter(ProductLine::costKnown)
                .sorted(java.util.Comparator.comparing(ProductLine::contribution).reversed()).toList();
        List<ProductLine> best = known.stream().filter(l -> l.contribution().signum() > 0).limit(5).toList();
        List<ProductLine> worst = known.reversed().stream().filter(l -> l.contribution().signum() < 0).limit(5).toList();

        return new Report(from, to, orders == null ? 0 : orders, units, Money.scale(revenue), Money.scale(commission),
                Money.scale(logistics), Money.scale(payout), Money.scale(cogs), noCost, op, Money.scale(opTotal),
                Money.scale(tax), taxKnown, Money.scale(pack), Money.scale(net), margin, (Integer) transit[0],
                Money.scale((BigDecimal) transit[1]), canceled == null ? 0 : canceled, best, worst, productsNoCost,
                noCost > 0 || !taxKnown);
    }
}
