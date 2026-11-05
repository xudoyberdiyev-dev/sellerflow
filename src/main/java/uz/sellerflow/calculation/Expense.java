package uz.sellerflow.calculation;

import uz.sellerflow.calculation.CalcTypes.AmountType;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.CalcTypes.ExpenseStatus;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Bitta xarajat kiritmasi.
 * KNOWN holatda: FIXED_PER_UNIT -> value = so'm/dona; PERCENT_OF_SALE_PRICE -> value = foiz (10 = 10%).
 * ZERO/UNKNOWN/NOT_SET holatda value = null.
 */
public record Expense(ExpenseCategory category, ExpenseStatus status, AmountType amountType, BigDecimal value) {

    public static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    public Expense {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        if (status == ExpenseStatus.KNOWN) {
            Objects.requireNonNull(amountType, "amountType");
            Objects.requireNonNull(value, "value");
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Expense value must be >= 0");
            }
            if (amountType == AmountType.PERCENT_OF_SALE_PRICE) {
                if (!category.allowsPercent()) {
                    throw new IllegalArgumentException(category + " cannot be a percent of sale price");
                }
                if (value.compareTo(MAX_PERCENT) > 0) {
                    throw new IllegalArgumentException("Percent must be <= 100");
                }
            } else if (value.compareTo(Money.MAX) > 0) {
                throw new IllegalArgumentException("Amount too large");
            }
        } else {
            amountType = null;
            value = null;
        }
    }

    public static Expense fixed(ExpenseCategory c, String amount) {
        return new Expense(c, ExpenseStatus.KNOWN, AmountType.FIXED_PER_UNIT, new BigDecimal(amount));
    }

    public static Expense percent(ExpenseCategory c, String percent) {
        return new Expense(c, ExpenseStatus.KNOWN, AmountType.PERCENT_OF_SALE_PRICE, new BigDecimal(percent));
    }

    public static Expense zero(ExpenseCategory c) {
        return new Expense(c, ExpenseStatus.ZERO, null, null);
    }

    public static Expense unknown(ExpenseCategory c) {
        return new Expense(c, ExpenseStatus.UNKNOWN, null, null);
    }

    public static Expense notSet(ExpenseCategory c) {
        return new Expense(c, ExpenseStatus.NOT_SET, null, null);
    }

    public boolean isKnownFixed() {
        return status == ExpenseStatus.KNOWN && amountType == AmountType.FIXED_PER_UNIT;
    }

    public boolean isKnownPercent() {
        return status == ExpenseStatus.KNOWN && amountType == AmountType.PERCENT_OF_SALE_PRICE;
    }

    /** Hisob aniqligiga ta'sir qiladimi (UNKNOWN yoki NOT_SET). */
    public boolean isMissing() {
        return status == ExpenseStatus.UNKNOWN || status == ExpenseStatus.NOT_SET;
    }
}
