package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.calculation.CalcTypes.AmountType;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.CalcTypes.ExpenseStatus;
import uz.sellerflow.calculation.CalcTypes.PriceGoal;
import uz.sellerflow.calculation.CalculationEngine;
import uz.sellerflow.calculation.CalculationEngine.ExpenseLine;
import uz.sellerflow.calculation.CalculationEngine.PriceResult;
import uz.sellerflow.calculation.CalculationEngine.ProfitResult;
import uz.sellerflow.calculation.Expense;
import uz.sellerflow.calculation.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Qoralama (session data) <-> engine kiritmalari <-> tarix snapshotlari o'rtasidagi o'girish.
 * Qoralama kalitlari: cost, price, goal, target, rs (yaxlitlash qadami), name, pid, ch, exp{K..O:{s,t,v}}.
 */
public final class CalcModel {
    private CalcModel() {}

    public static final BigDecimal DEFAULT_ROUNDING_STEP = new BigDecimal("1000"); // 05.11.2026 qarori

    public static String label(ExpenseCategory c) {
        return switch (c) {
            case COMMISSION -> "Komissiya";
            case LOGISTICS -> "Logistika";
            case PACKAGING -> "Qadoqlash";
            case ADS -> "Reklama";
            case TAX -> "Soliq (siz kiritgan)";
            case SERVICE_FEE -> "Xizmat / ekvayring to‘lovi";
            case OTHER -> "Boshqa xarajat";
        };
    }

    public static String goalLabel(String goal) {
        if (goal == null) return "—";
        return switch (PriceGoal.valueOf(goal)) {
            case TARGET_PROFIT -> "Maqsadli foyda summasi";
            case TARGET_MARGIN -> "Maqsadli marja";
            case BREAK_EVEN -> "Zararsizlik nuqtasi";
        };
    }

    public static String channelLabel(String ch) {
        if (ch == null) return "—";
        try {
            return SalesChannel.valueOf(ch).label();
        } catch (IllegalArgumentException e) {
            return ch;
        }
    }

    // ------------------------------------------------------------- expenses in draft

    public static Expense expense(Session s, ExpenseCategory c) {
        JsonNode n = s.data().path("exp").get(c.code());
        return fromNode(c, n);
    }

    static Expense fromNode(ExpenseCategory c, JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return Expense.notSet(c);
        ExpenseStatus st = ExpenseStatus.valueOf(n.path("s").asText("NOT_SET"));
        if (st == ExpenseStatus.KNOWN) {
            AmountType t = "PCT".equals(n.path("t").asText()) ? AmountType.PERCENT_OF_SALE_PRICE : AmountType.FIXED_PER_UNIT;
            return new Expense(c, ExpenseStatus.KNOWN, t, new BigDecimal(n.path("v").asText()));
        }
        return new Expense(c, st, null, null);
    }

    public static void setExpense(Session s, Expense e) {
        ObjectNode node = s.obj("exp").putObject(e.category().code());
        node.put("s", e.status().name());
        if (e.status() == ExpenseStatus.KNOWN) {
            node.put("t", e.amountType() == AmountType.PERCENT_OF_SALE_PRICE ? "PCT" : "FIX");
            node.put("v", e.value().toPlainString());
        }
    }

    public static Map<ExpenseCategory, Expense> expenses(Session s) {
        Map<ExpenseCategory, Expense> m = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) m.put(c, expense(s, c));
        return m;
    }

    public static String describe(Expense e) {
        return switch (e.status()) {
            case KNOWN -> e.amountType() == AmountType.PERCENT_OF_SALE_PRICE
                    ? Money.formatPercent(e.value()) + "% (narxdan)"
                    : Money.format(e.value()) + " so‘m";
            case ZERO -> "0 (yo‘q)";
            case UNKNOWN -> "noma’lum";
            case NOT_SET -> "kiritilmagan";
        };
    }

    // ------------------------------------------------------------- engine calls

    public static ProfitResult computeProfit(Session s) {
        io.micrometer.core.instrument.Metrics.counter("sellerflow.calculations", "type", "profit").increment();
        return CalculationEngine.profit(s.dec("price"), s.dec("cost"), expenses(s));
    }

    public static BigDecimal roundingStep(Session s) {
        BigDecimal rs = s.dec("rs");
        return rs == null ? DEFAULT_ROUNDING_STEP : rs;
    }

    public static PriceResult computePrice(Session s) {
        PriceGoal goal = PriceGoal.valueOf(s.str("goal"));
        BigDecimal target = s.dec("target");
        io.micrometer.core.instrument.Metrics.counter("sellerflow.calculations", "type", "price").increment();
        return CalculationEngine.price(goal, s.dec("cost"), expenses(s),
                goal == PriceGoal.TARGET_PROFIT ? target : null,
                goal == PriceGoal.TARGET_MARGIN ? target : null,
                roundingStep(s));
    }

    public static String missingList(List<ExpenseCategory> missing) {
        return missing.stream().map(CalcModel::label).collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------- snapshots

    /** Kiritma snapshoti: qayta hisoblashda yangi qoralamani tiklash uchun yetarli. */
    public static String inputSnapshot(ObjectMapper m, Session s, String kind) {
        ObjectNode n = m.createObjectNode();
        n.put("kind", kind);
        n.put("cost", s.str("cost"));
        if ("PROFIT".equals(kind)) {
            n.put("price", s.str("price"));
        } else {
            n.put("goal", s.str("goal"));
            if (s.str("target") != null) n.put("target", s.str("target"));
            n.put("roundingStep", roundingStep(s).toPlainString());
        }
        return n.toString();
    }

    public static String expenseSnapshot(ObjectMapper m, Session s, List<ExpenseLine> lines) {
        ObjectNode n = m.createObjectNode();
        for (ExpenseCategory c : ExpenseCategory.values()) {
            Expense e = expense(s, c);
            ObjectNode x = n.putObject(c.code());
            x.put("status", e.status().name());
            if (e.status() == ExpenseStatus.KNOWN) {
                x.put("type", e.amountType().name());
                x.put("value", e.value().toPlainString());
            }
            if (lines != null) {
                lines.stream().filter(l -> l.category() == c && l.amount() != null).findFirst()
                        .ifPresent(l -> x.put("amount", l.amount().toPlainString()));
            }
        }
        return n.toString();
    }

    public static String profitOutput(ObjectMapper m, ProfitResult r) {
        ObjectNode n = m.createObjectNode();
        n.put("salePrice", r.salePrice().toPlainString());
        n.put("unitCost", r.unitCost().toPlainString());
        n.put("knownExpensesTotal", r.knownExpensesTotal().toPlainString());
        n.put("profit", r.profit().toPlainString());
        n.put("marginPct", r.marginPct().toPlainString());
        n.put("outcome", r.outcome().name());
        n.put("accuracy", r.accuracy().name());
        return n.toString();
    }

    public static String priceOutput(ObjectMapper m, PriceResult r) {
        ObjectNode n = m.createObjectNode();
        n.put("goal", r.goal().name());
        n.put("status", r.status().name());
        n.put("fixedTotal", r.fixedTotal().toPlainString());
        n.put("percentRate", r.percentRate().stripTrailingZeros().toPlainString());
        if (r.targetProfit() != null) n.put("targetProfit", r.targetProfit().toPlainString());
        if (r.targetMarginPct() != null) n.put("targetMarginPct", r.targetMarginPct().toPlainString());
        if (r.rawPrice() != null) n.put("rawPrice", r.rawPrice().toPlainString());
        n.put("roundingStep", r.roundingStep().toPlainString());
        if (r.recommendedPrice() != null) {
            n.put("recommendedPrice", r.recommendedPrice().toPlainString());
            n.put("profitAtPrice", r.verification().profit().toPlainString());
            n.put("marginAtPrice", r.verification().marginPct().toPlainString());
            n.put("knownExpensesAtPrice", r.verification().knownExpensesTotal().toPlainString());
        }
        n.put("accuracy", r.accuracy().name());
        return n.toString();
    }

    /** Snapshotdan yangi qoralama ma'lumotlarini tiklash (UX-HIST-031). */
    public static void restoreDraft(ObjectMapper m, Session s, String inputJson, String expenseJson) {
        try {
            JsonNode in = m.readTree(inputJson);
            JsonNode ex = m.readTree(expenseJson);
            s.put("cost", in.path("cost").asText(null));
            if (in.hasNonNull("price")) s.put("price", in.path("price").asText());
            if (in.hasNonNull("goal")) s.put("goal", in.path("goal").asText());
            if (in.hasNonNull("target")) s.put("target", in.path("target").asText());
            if (in.hasNonNull("roundingStep")) s.put("rs", in.path("roundingStep").asText());
            for (ExpenseCategory c : ExpenseCategory.values()) {
                JsonNode x = ex.get(c.code());
                if (x == null) continue;
                ExpenseStatus st = ExpenseStatus.valueOf(x.path("status").asText("NOT_SET"));
                if (st == ExpenseStatus.KNOWN) {
                    AmountType t = AmountType.valueOf(x.path("type").asText());
                    setExpense(s, new Expense(c, st, t, new BigDecimal(x.path("value").asText())));
                } else {
                    setExpense(s, new Expense(c, st, null, null));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot restore snapshot", e);
        }
    }
}
