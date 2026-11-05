package uz.sellerflow.calculation;

/**
 * Calculation Engine uchun umumiy enumlar.
 * Manba: SF-PRD-02.04 (foyda), SF-PRD-02.05 (narx), SF-ADDENDUM-017 (CR-FIN-01/02).
 */
public final class CalcTypes {
    private CalcTypes() {}

    /** Xarajat holati. UNKNOWN va NOT_SET hech qachon tasdiqlangan 0 emas (UX-CALC-016/017). */
    public enum ExpenseStatus { KNOWN, ZERO, UNKNOWN, NOT_SET }

    /** Xarajat qanday kiritilgan: bir dona uchun summa yoki sotish narxining foizi. */
    public enum AmountType { FIXED_PER_UNIT, PERCENT_OF_SALE_PRICE }

    public enum Accuracy { COMPLETE_INPUT, PARTIAL_ESTIMATE }

    public enum ProfitOutcome { PROFIT, BREAK_EVEN, LOSS }

    public enum PriceGoal { TARGET_PROFIT, TARGET_MARGIN, BREAK_EVEN }

    /**
     * Narx hisoblash natijasi turi (SF-PRD-02.05, 10.3-jadval).
     * NO_SOLUTION — hech qanday musbat narx maqsadga yetmaydi.
     * NO_UNIQUE_PRICE — har qanday musbat narx mos, yagona iqtisodiy narx yo'q.
     */
    public enum PriceStatus { OK, NO_SOLUTION, NO_UNIQUE_PRICE, OUT_OF_RANGE }

    /**
     * Xarajat kategoriyalari. allowsPercent — sotish narxi foizi sifatida kiritish mumkinmi.
     * K va T — APPROVED; A va E — CR-FIN-01 (05.11.2026 da loyiha egasi "default" bilan qabul qildi).
     */
    public enum ExpenseCategory {
        COMMISSION("K", true),
        LOGISTICS("L", false),
        PACKAGING("Q", false),
        ADS("A", true),
        TAX("T", true),
        SERVICE_FEE("E", true),
        OTHER("O", false);

        private final String code;
        private final boolean allowsPercent;

        ExpenseCategory(String code, boolean allowsPercent) {
            this.code = code;
            this.allowsPercent = allowsPercent;
        }

        public String code() { return code; }
        public boolean allowsPercent() { return allowsPercent; }

        public static ExpenseCategory byCode(String code) {
            for (ExpenseCategory c : values()) {
                if (c.code.equals(code)) return c;
            }
            throw new IllegalArgumentException("Unknown expense code: " + code);
        }

        /** Ixtiyoriy qo'shimcha xarajatlar (extras). */
        public boolean isExtra() {
            return this != COMMISSION && this != LOGISTICS;
        }
    }
}
