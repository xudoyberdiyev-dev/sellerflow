package uz.sellerflow.calculation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.sellerflow.calculation.CalcTypes.Accuracy;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.CalcTypes.PriceGoal;
import uz.sellerflow.calculation.CalcTypes.PriceStatus;
import uz.sellerflow.calculation.CalcTypes.ProfitOutcome;
import uz.sellerflow.calculation.CalculationEngine.PriceResult;
import uz.sellerflow.calculation.CalculationEngine.ProfitResult;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uz.sellerflow.calculation.CalcTypes.ExpenseCategory.*;

/** Master PRD 20.2 — Calculation Engine regression matrix. */
class CalculationEngineTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Map<ExpenseCategory, Expense> map(Expense... es) {
        Map<ExpenseCategory, Expense> m = new EnumMap<>(ExpenseCategory.class);
        for (Expense e : es) m.put(e.category(), e);
        return m;
    }

    private static Map<ExpenseCategory, Expense> allZeroExcept(Expense... es) {
        Map<ExpenseCategory, Expense> m = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) m.put(c, Expense.zero(c));
        for (Expense e : es) m.put(e.category(), e);
        return m;
    }

    @Test
    void approvedTeachingExample_200k_profit40k_margin20() {
        ProfitResult r = CalculationEngine.profit(bd("200000"), bd("120000"), map(
                Expense.percent(COMMISSION, "10"),
                Expense.fixed(LOGISTICS, "5000"),
                Expense.fixed(PACKAGING, "3000"),
                Expense.fixed(ADS, "7000"),
                Expense.fixed(TAX, "4000"),
                Expense.zero(SERVICE_FEE),
                Expense.fixed(OTHER, "1000")));
        assertThat(r.knownExpensesTotal()).isEqualByComparingTo("160000");
        assertThat(r.profit()).isEqualByComparingTo("40000");
        assertThat(r.marginPct()).isEqualByComparingTo("20");
        assertThat(r.outcome()).isEqualTo(ProfitOutcome.PROFIT);
        assertThat(r.accuracy()).isEqualTo(Accuracy.COMPLETE_INPUT);
    }

    @Test
    void sameArithmeticButExtrasNotSet_isPartialEstimate() {
        ProfitResult r = CalculationEngine.profit(bd("200000"), bd("160000"), map(
                Expense.zero(COMMISSION), Expense.zero(LOGISTICS)));
        assertThat(r.profit()).isEqualByComparingTo("40000");
        assertThat(r.accuracy()).isEqualTo(Accuracy.PARTIAL_ESTIMATE);
        assertThat(r.missing()).containsExactly(PACKAGING, ADS, TAX, SERVICE_FEE, OTHER);
    }

    @Test
    void loss() {
        ProfitResult r = CalculationEngine.profit(bd("100000"), bd("120000"), allZeroExcept());
        assertThat(r.profit()).isEqualByComparingTo("-20000");
        assertThat(r.marginPct()).isEqualByComparingTo("-20");
        assertThat(r.outcome()).isEqualTo(ProfitOutcome.LOSS);
        assertThat(r.accuracy()).isEqualTo(Accuracy.COMPLETE_INPUT);
    }

    @Test
    void breakEven() {
        ProfitResult r = CalculationEngine.profit(bd("100000"), bd("100000"), allZeroExcept());
        assertThat(r.profit()).isEqualByComparingTo("0");
        assertThat(r.outcome()).isEqualTo(ProfitOutcome.BREAK_EVEN);
    }

    @Test
    void lossAndPartialCanCoexist() {
        ProfitResult r = CalculationEngine.profit(bd("100000"), bd("120000"),
                map(Expense.unknown(LOGISTICS)));
        assertThat(r.outcome()).isEqualTo(ProfitOutcome.LOSS);
        assertThat(r.accuracy()).isEqualTo(Accuracy.PARTIAL_ESTIMATE);
    }

    @Test
    void zeroOrNegativePriceRejected_negativeCostRejected_zeroCostAccepted() {
        assertThatThrownBy(() -> CalculationEngine.profit(bd("0"), bd("1"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculationEngine.profit(bd("-5"), bd("1"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculationEngine.profit(bd("100"), bd("-1"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        ProfitResult r = CalculationEngine.profit(bd("100"), bd("0"), allZeroExcept());
        assertThat(r.profit()).isEqualByComparingTo("100");
    }

    @Test
    void percentCommissionAppliedOnce() {
        ProfitResult r = CalculationEngine.profit(bd("200000"), bd("0"),
                allZeroExcept(Expense.percent(COMMISSION, "10")));
        assertThat(r.lines().get(0).amount()).isEqualByComparingTo("20000");
        assertThat(r.knownExpensesTotal()).isEqualByComparingTo("20000");
    }

    @Test
    void unknownLogistics_partial_and_explicitZeroExtras_complete() {
        ProfitResult partial = CalculationEngine.profit(bd("200000"), bd("100000"),
                allZeroExcept(Expense.unknown(LOGISTICS)));
        assertThat(partial.accuracy()).isEqualTo(Accuracy.PARTIAL_ESTIMATE);
        ProfitResult complete = CalculationEngine.profit(bd("200000"), bd("100000"), allZeroExcept());
        assertThat(complete.accuracy()).isEqualTo(Accuracy.COMPLETE_INPUT);
    }

    @Test
    void serviceFeePercentOfSalePrice_addendumExample() {
        ProfitResult r = CalculationEngine.profit(bd("200000"), bd("0"),
                allZeroExcept(Expense.percent(SERVICE_FEE, "1.5"), Expense.fixed(ADS, "5000")));
        assertThat(r.lines().get(SERVICE_FEE.ordinal()).amount()).isEqualByComparingTo("3000");
        assertThat(r.lines().get(ADS.ordinal()).amount()).isEqualByComparingTo("5000");
    }

    @Test
    void percentNotAllowedForLogistics() {
        assertThatThrownBy(() -> Expense.percent(LOGISTICS, "5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Expense.percent(OTHER, "5")).isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------------- PRICE

    private static Map<ExpenseCategory, Expense> f130kR10() {
        // F = 130 000 (tannarx 125 000 + logistika 5 000), r = 10% komissiya
        return allZeroExcept(Expense.percent(COMMISSION, "10"), Expense.fixed(LOGISTICS, "5000"));
    }

    @Test
    void targetProfit25k_step1000_gives173000_profit25700() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("125000"), f130kR10(),
                bd("25000"), null, bd("1000"));
        assertThat(r.status()).isEqualTo(PriceStatus.OK);
        assertThat(r.rawPrice()).isEqualByComparingTo("172222.22");
        assertThat(r.recommendedPrice()).isEqualByComparingTo("173000");
        assertThat(r.verification().profit()).isEqualByComparingTo("25700");
        assertThat(r.verification().marginPct()).isEqualByComparingTo("14.86");
        assertThat(r.accuracy()).isEqualTo(Accuracy.COMPLETE_INPUT);
    }

    @Test
    void targetMargin20_step1000_gives186000() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("125000"), f130kR10(),
                null, bd("20"), bd("1000"));
        assertThat(r.recommendedPrice()).isEqualByComparingTo("186000");
        assertThat(r.verification().profit()).isEqualByComparingTo("37400");
        assertThat(r.verification().marginPct()).isEqualByComparingTo("20.11");
    }

    @Test
    void breakEven_step1000_gives145000_profit500() {
        PriceResult r = CalculationEngine.price(PriceGoal.BREAK_EVEN, bd("125000"), f130kR10(),
                null, null, bd("1000"));
        assertThat(r.recommendedPrice()).isEqualByComparingTo("145000");
        assertThat(r.verification().profit()).isEqualByComparingTo("500");
    }

    @ParameterizedTest
    @CsvSource({"1,172223", "100,172300", "1000,173000"})
    void roundingSteps(String step, String expected) {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("125000"), f130kR10(),
                bd("25000"), null, bd(step));
        assertThat(r.recommendedPrice()).isEqualByComparingTo(expected);
        assertThat(r.verification().profit()).isGreaterThanOrEqualTo(bd("25000"));
    }

    @Test
    void zeroDenominator_FGzero_r1_noUniquePrice_noDivisionByZero() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("0"),
                allZeroExcept(Expense.percent(COMMISSION, "100")), bd("0"), null, bd("1"));
        assertThat(r.status()).isEqualTo(PriceStatus.NO_UNIQUE_PRICE);
    }

    @Test
    void targetImpossible_whenPercentsAtLeast100() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("1000"),
                allZeroExcept(Expense.percent(COMMISSION, "60"), Expense.percent(TAX, "45")),
                bd("0"), null, bd("1"));
        assertThat(r.status()).isEqualTo(PriceStatus.NO_SOLUTION);
    }

    @Test
    void marginImpossible_whenRPlusMAtLeast1() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("1000"),
                allZeroExcept(Expense.percent(COMMISSION, "30")), null, bd("70"), bd("1"));
        assertThat(r.status()).isEqualTo(PriceStatus.NO_SOLUTION);
    }

    @Test
    void zeroF_zeroG_rBelow1_noUniquePrice() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("0"),
                allZeroExcept(Expense.percent(COMMISSION, "10")), bd("0"), null, bd("1"));
        assertThat(r.status()).isEqualTo(PriceStatus.NO_UNIQUE_PRICE);
    }

    @Test
    void zeroF_marginCases() {
        Map<ExpenseCategory, Expense> e = allZeroExcept(Expense.percent(COMMISSION, "30"));
        assertThat(CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("0"), e, null, bd("70"), bd("1")).status())
                .isEqualTo(PriceStatus.NO_UNIQUE_PRICE);
        assertThat(CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("0"), e, null, bd("71"), bd("1")).status())
                .isEqualTo(PriceStatus.NO_SOLUTION);
    }

    @Test
    void zeroF_breakEvenCases() {
        assertThat(CalculationEngine.price(PriceGoal.BREAK_EVEN, bd("0"),
                allZeroExcept(Expense.percent(COMMISSION, "100")), null, null, bd("1")).status())
                .isEqualTo(PriceStatus.NO_UNIQUE_PRICE);
    }

    @Test
    void unknownExpense_priceIsPartialEstimate() {
        PriceResult r = CalculationEngine.price(PriceGoal.BREAK_EVEN, bd("100000"),
                map(Expense.unknown(COMMISSION), Expense.fixed(LOGISTICS, "0")), null, null, bd("1000"));
        assertThat(r.status()).isEqualTo(PriceStatus.OK);
        assertThat(r.accuracy()).isEqualTo(Accuracy.PARTIAL_ESTIMATE);
        assertThat(r.missing()).contains(COMMISSION);
    }

    @Test
    void priceAndProfitParity() {
        // PRD-02.05 tavsiya narxi PRD-02.04 profit engine bilan bir xil natija beradi
        PriceResult p = CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("87650"),
                allZeroExcept(Expense.percent(COMMISSION, "12.5"), Expense.fixed(LOGISTICS, "7300"),
                        Expense.percent(SERVICE_FEE, "1.5")), null, bd("18"), bd("100"));
        ProfitResult direct = CalculationEngine.profit(p.recommendedPrice(), bd("87650"),
                allZeroExcept(Expense.percent(COMMISSION, "12.5"), Expense.fixed(LOGISTICS, "7300"),
                        Expense.percent(SERVICE_FEE, "1.5")));
        assertThat(direct.profit()).isEqualByComparingTo(p.verification().profit());
        assertThat(direct.profit().multiply(bd("100")))
                .isGreaterThanOrEqualTo(bd("18").multiply(p.recommendedPrice()));
    }

    @Test
    void invalidRoundingStepRejected() {
        assertThatThrownBy(() -> CalculationEngine.price(PriceGoal.BREAK_EVEN, bd("1"), Map.of(), null, null, bd("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------------- PARSING

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "120000|120000", "120 000|120000", "120.000|120000", "1,250,000|1250000",
            "1 250 000 so'm|1250000", "1,5|1.5", "12.5|12.5", "10%|10", "0|0"})
    void parsesUserInput(String in, String expected) {
        assertThat(Money.parse(in)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"abc", "-5", "1.234.5", "1,2,3", "''", "12.345"})
    void rejectsInvalidInput(String in) {
        // "12.345" — 3 xonali kasr; noaniq bo'lgani uchun ming ajratuvchi deb olinadi -> 12345
        if (in.equals("12.345")) {
            assertThat(Money.parse(in)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("12345"));
            return;
        }
        assertThat(Money.parse(in)).isEmpty();
    }

    @Test
    void formatting() {
        assertThat(Money.format(bd("172300"))).isEqualTo("172 300");
        assertThat(Money.format(bd("-20000"))).isEqualTo("-20 000");
        assertThat(Money.format(bd("2583.35"))).isEqualTo("2 583,35");
        assertThat(Money.formatPercent(bd("14.8555"))).isEqualTo("14,86");
        assertThat(Money.formatPercent(bd("20.00"))).isEqualTo("20");
    }

    // ------------------------------------------------------------ LOW (hisob) regression, 2026-11-07

    /** Fuzz topgan holat: foiz xarajat yaxlitlashi tufayli 842 396 ham maqsadga yetadi — tavsiya 1 qadam past bo'lishi kerak. */
    @Test
    void recommendedPrice_isMinimalValidStep_notOneStepHigher() {
        var ex = allZeroExcept(Expense.percent(COMMISSION, "32.93"));
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, bd("525098"), ex, bd("39897"), null, bd("1"));
        assertThat(r.status()).isEqualTo(PriceStatus.OK);
        assertThat(r.recommendedPrice()).isEqualByComparingTo("842396");
        assertThat(CalculationEngine.profit(r.recommendedPrice(), bd("525098"), ex).profit()).isGreaterThanOrEqualTo(bd("39897"));
        assertThat(CalculationEngine.profit(r.recommendedPrice().subtract(BigDecimal.ONE), bd("525098"), ex).profit())
                .isLessThan(bd("39897"));

        var be = allZeroExcept(Expense.percent(COMMISSION, "9.95"));
        PriceResult b = CalculationEngine.price(PriceGoal.BREAK_EVEN, bd("788476"), be, null, null, bd("1"));
        assertThat(b.recommendedPrice()).isEqualByComparingTo("875598");
    }

    /** Fuzz: tavsiya narxi doim maqsadga yetadi va bir qadam pasti yetmaydi. */
    @Test
    void recommendedPrice_minimality_fuzz() {
        java.util.Random rnd = new java.util.Random(7);
        BigDecimal[] steps = {bd("1"), bd("100"), bd("1000")};
        int checked = 0;
        for (int i = 0; i < 20000; i++) {
            var ex = allZeroExcept(Expense.percent(COMMISSION, new BigDecimal(rnd.nextInt(5000)).movePointLeft(2).toPlainString()),
                    Expense.fixed(LOGISTICS, String.valueOf(rnd.nextInt(50000))));
            BigDecimal cost = new BigDecimal(rnd.nextInt(2_000_000));
            BigDecimal step = steps[rnd.nextInt(3)];
            BigDecimal tp = new BigDecimal(rnd.nextInt(100_000));
            PriceResult r = CalculationEngine.price(PriceGoal.TARGET_PROFIT, cost, ex, tp, null, step);
            if (r.status() != PriceStatus.OK) continue;
            checked++;
            assertThat(CalculationEngine.profit(r.recommendedPrice(), cost, ex).profit()).isGreaterThanOrEqualTo(tp);
            BigDecimal lower = r.recommendedPrice().subtract(step);
            if (lower.signum() > 0) {
                assertThat(CalculationEngine.profit(lower, cost, ex).profit()).as("i=" + i).isLessThan(tp);
            }
        }
        assertThat(checked).isGreaterThan(10000);
    }

    @Test
    void marginWithManyDecimals_doesNotThrow() {
        PriceResult r = CalculationEngine.price(PriceGoal.TARGET_MARGIN, bd("100000"), allZeroExcept(),
                null, bd("12.3456789"), bd("100"));
        assertThat(r.status()).isEqualTo(PriceStatus.OK);
    }

    @Test
    void decimalWithLeadingZero_isNotThousands() {
        // "0,075" endi 75 deb o'qilmaydi; summa 2 xonagacha bo'lgani uchun rad etiladi (foydalanuvchi qayta kiritadi)
        assertThat(Money.parse("0,075")).isEmpty();
        assertThat(Money.parse("0,75")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.75"));
        assertThat(Money.parse("1,075")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1075"));
    }

    @Test
    void percentParsing_rejectsThousandsSeparators() {
        assertThat(Money.parsePercent("12,5")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("12.5"));
        assertThat(Money.parsePercent("7.25%")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("7.25"));
        assertThat(Money.parsePercent("0,075")).isEmpty();   // 75% emas
        assertThat(Money.parsePercent("1,000")).isEmpty();
        assertThat(Money.parsePercent("1.000")).isEmpty();
        assertThat(Money.parsePercent("-5")).isEmpty();
        assertThat(Money.parsePercent("abc")).isEmpty();
    }
}
