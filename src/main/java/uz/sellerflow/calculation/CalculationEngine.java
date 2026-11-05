package uz.sellerflow.calculation;

import uz.sellerflow.calculation.CalcTypes.Accuracy;
import uz.sellerflow.calculation.CalcTypes.AmountType;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.CalcTypes.ExpenseStatus;
import uz.sellerflow.calculation.CalcTypes.PriceGoal;
import uz.sellerflow.calculation.CalcTypes.PriceStatus;
import uz.sellerflow.calculation.CalcTypes.ProfitOutcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Yagona moliyaviy engine. Bot ham, kelajakdagi web ham shu klassdan foydalanadi.
 * Formulalar: SF-PRD-02.04 (9.4) va SF-PRD-02.05 (10.2–10.4).
 */
public final class CalculationEngine {

    /** Har bir saqlangan natijaga yoziladi (UX-HIST-022). */
    public static final String VERSION = "1.0.0";

    public static final Set<BigDecimal> ALLOWED_ROUNDING_STEPS =
            Set.of(BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("1000"));

    private static final int RAW_SCALE = 10;
    private static final int MAX_VERIFY_STEPS = 50;

    private CalculationEngine() {}

    // ------------------------------------------------------------------ PROFIT

    public record ExpenseLine(ExpenseCategory category, ExpenseStatus status, AmountType amountType,
                              BigDecimal percent, BigDecimal amount) {}

    public record ProfitResult(BigDecimal salePrice, BigDecimal unitCost, List<ExpenseLine> lines,
                               BigDecimal knownExpensesTotal, BigDecimal profit, BigDecimal marginPct,
                               ProfitOutcome outcome, Accuracy accuracy, List<ExpenseCategory> missing) {}

    /**
     * KnownExpenses = C + known(K..O); Profit = P - KnownExpenses; Margin = Profit / P * 100.
     * Foizli xarajat faqat bir marta, P bazasida hisoblanadi (UX-CALC-009, 024).
     */
    public static ProfitResult profit(BigDecimal salePrice, BigDecimal unitCost, Map<ExpenseCategory, Expense> expenses) {
        Objects.requireNonNull(salePrice, "salePrice");
        Objects.requireNonNull(unitCost, "unitCost");
        if (salePrice.signum() <= 0) throw new IllegalArgumentException("Sale price must be > 0");
        if (unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost must be >= 0");
        if (salePrice.compareTo(Money.MAX) > 0 || unitCost.compareTo(Money.MAX) > 0) {
            throw new IllegalArgumentException("Value out of range");
        }
        Map<ExpenseCategory, Expense> all = normalize(expenses);

        List<ExpenseLine> lines = new ArrayList<>();
        List<ExpenseCategory> missing = new ArrayList<>();
        BigDecimal total = Money.scale(unitCost);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            Expense e = all.get(c);
            BigDecimal amount = null;
            BigDecimal percent = null;
            if (e.isKnownFixed()) {
                amount = Money.scale(e.value());
            } else if (e.isKnownPercent()) {
                percent = e.value();
                amount = Money.scale(salePrice.multiply(e.value()).divide(Money.HUNDRED, RAW_SCALE, RoundingMode.HALF_UP));
            } else if (e.status() == ExpenseStatus.ZERO) {
                amount = Money.scale(BigDecimal.ZERO);
            } else {
                missing.add(c);
            }
            if (amount != null) total = total.add(amount);
            lines.add(new ExpenseLine(c, e.status(), e.amountType(), percent, amount));
        }
        BigDecimal profit = Money.scale(salePrice.subtract(total));
        BigDecimal margin = profit.multiply(Money.HUNDRED).divide(salePrice, 2, RoundingMode.HALF_UP);
        ProfitOutcome outcome = profit.signum() > 0 ? ProfitOutcome.PROFIT
                : profit.signum() == 0 ? ProfitOutcome.BREAK_EVEN : ProfitOutcome.LOSS;
        Accuracy accuracy = missing.isEmpty() ? Accuracy.COMPLETE_INPUT : Accuracy.PARTIAL_ESTIMATE;
        return new ProfitResult(Money.scale(salePrice), Money.scale(unitCost), List.copyOf(lines), total,
                profit, margin, outcome, accuracy, List.copyOf(missing));
    }

    // ------------------------------------------------------------------- PRICE

    public record PriceResult(PriceGoal goal, PriceStatus status, BigDecimal fixedTotal, BigDecimal percentRate,
                              BigDecimal targetProfit, BigDecimal targetMarginPct, BigDecimal rawPrice,
                              BigDecimal roundingStep, BigDecimal recommendedPrice, ProfitResult verification,
                              Accuracy accuracy, List<ExpenseCategory> missing) {}

    /**
     * F = C + ma'lum qat'iy xarajatlar; r = ma'lum P-foizlari ulushi.
     * Maqsadli foyda: (F+G)/(1-r); marja: F/(1-r-m); zararsizlik: F/(1-r).
     * Maxsus holatlar SF-PRD-02.05 10.3-jadvaliga muvofiq.
     *
     * @param targetProfit    G (so'm), faqat TARGET_PROFIT uchun, >= 0
     * @param targetMarginPct m (foiz, 20 = 20%), faqat TARGET_MARGIN uchun, 0 <= m < 100
     */
    public static PriceResult price(PriceGoal goal, BigDecimal unitCost, Map<ExpenseCategory, Expense> expenses,
                                    BigDecimal targetProfit, BigDecimal targetMarginPct, BigDecimal roundingStep) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(unitCost, "unitCost");
        if (unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost must be >= 0");
        if (roundingStep == null || ALLOWED_ROUNDING_STEPS.stream().noneMatch(s -> s.compareTo(roundingStep) == 0)) {
            throw new IllegalArgumentException("Rounding step must be 1, 100 or 1000");
        }
        BigDecimal g = BigDecimal.ZERO;
        BigDecimal m = BigDecimal.ZERO;
        if (goal == PriceGoal.TARGET_PROFIT) {
            Objects.requireNonNull(targetProfit, "targetProfit");
            if (targetProfit.signum() < 0) throw new IllegalArgumentException("Target profit must be >= 0");
            g = targetProfit;
        } else if (goal == PriceGoal.TARGET_MARGIN) {
            Objects.requireNonNull(targetMarginPct, "targetMarginPct");
            if (targetMarginPct.signum() < 0 || targetMarginPct.compareTo(Money.HUNDRED) >= 0) {
                throw new IllegalArgumentException("Target margin must be in [0, 100)");
            }
            m = targetMarginPct.divide(Money.HUNDRED, RAW_SCALE, RoundingMode.UNNECESSARY);
        }

        Map<ExpenseCategory, Expense> all = normalize(expenses);
        BigDecimal f = Money.scale(unitCost);
        BigDecimal pctSum = BigDecimal.ZERO;
        List<ExpenseCategory> missing = new ArrayList<>();
        for (ExpenseCategory c : ExpenseCategory.values()) {
            Expense e = all.get(c);
            if (e.isKnownFixed()) f = f.add(Money.scale(e.value()));
            else if (e.isKnownPercent()) pctSum = pctSum.add(e.value());
            else if (e.isMissing()) missing.add(c);
        }
        BigDecimal r = pctSum.divide(Money.HUNDRED, RAW_SCALE, RoundingMode.UNNECESSARY);
        Accuracy accuracy = missing.isEmpty() ? Accuracy.COMPLETE_INPUT : Accuracy.PARTIAL_ESTIMATE;
        BigDecimal one = BigDecimal.ONE;

        PriceStatus special = null;
        BigDecimal numerator;
        BigDecimal denominator;
        switch (goal) {
            case TARGET_PROFIT -> {
                numerator = f.add(g);
                denominator = one.subtract(r);
                if (numerator.signum() > 0) {
                    if (denominator.signum() <= 0) special = PriceStatus.NO_SOLUTION;
                } else { // F = G = 0
                    special = r.compareTo(one) <= 0 ? PriceStatus.NO_UNIQUE_PRICE : PriceStatus.NO_SOLUTION;
                }
            }
            case TARGET_MARGIN -> {
                numerator = f;
                denominator = one.subtract(r).subtract(m);
                if (f.signum() > 0) {
                    if (denominator.signum() <= 0) special = PriceStatus.NO_SOLUTION;
                } else {
                    special = r.add(m).compareTo(one) <= 0 ? PriceStatus.NO_UNIQUE_PRICE : PriceStatus.NO_SOLUTION;
                }
            }
            case BREAK_EVEN -> {
                numerator = f;
                denominator = one.subtract(r);
                if (f.signum() > 0) {
                    if (denominator.signum() <= 0) special = PriceStatus.NO_SOLUTION;
                } else {
                    special = r.compareTo(one) <= 0 ? PriceStatus.NO_UNIQUE_PRICE : PriceStatus.NO_SOLUTION;
                }
            }
            default -> throw new IllegalStateException();
        }

        BigDecimal targetProfitOut = goal == PriceGoal.TARGET_PROFIT ? Money.scale(g) : null;
        BigDecimal targetMarginOut = goal == PriceGoal.TARGET_MARGIN ? targetMarginPct : null;
        if (special != null) {
            return new PriceResult(goal, special, f, r, targetProfitOut, targetMarginOut, null, roundingStep,
                    null, null, accuracy, List.copyOf(missing));
        }

        BigDecimal raw = numerator.divide(denominator, RAW_SCALE, RoundingMode.HALF_UP);
        BigDecimal candidate = ceilToStep(raw, roundingStep);
        if (candidate.signum() <= 0) candidate = roundingStep;

        // Yaxlitlangandan keyin PRD-02.04 engine orqali qayta tekshirish (UX-PRICE-025/026, UX-PRICE-041)
        for (int i = 0; i <= MAX_VERIFY_STEPS; i++) {
            if (candidate.compareTo(Money.MAX) > 0) break;
            ProfitResult check = profit(candidate, unitCost, all);
            if (meetsTarget(goal, check, g, targetMarginPct)) {
                return new PriceResult(goal, PriceStatus.OK, f, r, targetProfitOut, targetMarginOut,
                        raw.setScale(2, RoundingMode.HALF_UP), roundingStep, candidate, check, accuracy,
                        List.copyOf(missing));
            }
            candidate = candidate.add(roundingStep);
        }
        return new PriceResult(goal, PriceStatus.OUT_OF_RANGE, f, r, targetProfitOut, targetMarginOut,
                raw.setScale(2, RoundingMode.HALF_UP), roundingStep, null, null, accuracy, List.copyOf(missing));
    }

    public static BigDecimal ceilToStep(BigDecimal raw, BigDecimal step) {
        return raw.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    private static boolean meetsTarget(PriceGoal goal, ProfitResult check, BigDecimal g, BigDecimal mPct) {
        return switch (goal) {
            case TARGET_PROFIT -> check.profit().compareTo(g) >= 0;
            case BREAK_EVEN -> check.profit().signum() >= 0;
            // profit * 100 >= m% * P — aniq taqqoslash, yaxlitlangan marjasiz
            case TARGET_MARGIN -> check.profit().multiply(Money.HUNDRED)
                    .compareTo(mPct.multiply(check.salePrice())) >= 0;
        };
    }

    /** Yetishmayotgan kategoriyalar NOT_SET bo'ladi (UX-CALC-016: kiritilmagan qiymat avtomatik ZERO emas). */
    public static Map<ExpenseCategory, Expense> normalize(Map<ExpenseCategory, Expense> expenses) {
        Map<ExpenseCategory, Expense> all = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            Expense e = expenses == null ? null : expenses.get(c);
            if (e != null && e.category() != c) throw new IllegalArgumentException("Category mismatch for " + c);
            all.put(c, e == null ? Expense.notSet(c) : e);
        }
        return all;
    }
}
