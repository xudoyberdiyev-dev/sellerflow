package uz.sellerflow.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pul va foiz yordamchilari. double/float ishlatilmaydi (System Design 19.4).
 *
 * Kiritish normalizatsiyasi (Technical Design qarori, docs/DECISIONS.md):
 *  - bo'shliq, '_' va "so'm"/"sum"/"%" belgilari olib tashlanadi;
 *  - "120.000" yoki "1,250,000" kabi 3 xonali guruhlar ming ajratuvchi deb olinadi;
 *  - aks holda vergul o'nlik ajratuvchi sifatida nuqtaga aylantiriladi ("1,5" -> 1.5);
 *  - ko'pi bilan 2 ta o'nlik xona.
 */
public final class Money {
    private Money() {}

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    /** Texnik diapazon: 999 999 999 999 so'm. */
    public static final BigDecimal MAX = new BigDecimal("999999999999");
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Minglik ajratgich: birinchi guruh 0 bilan boshlanmaydi ("0,075" — o'nli kasr, 75 emas). */
    private static final Pattern THOUSANDS = Pattern.compile("^[1-9]\\d{0,2}([.,]\\d{3})+$");
    private static final Pattern NUMBER = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    public static BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, ROUNDING);
    }

    /** Foydalanuvchi matnini manfiy bo'lmagan songa aylantiradi; yaroqsiz bo'lsa Optional.empty(). */
    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("so'm", "").replace("so‘m", "").replace("sum", "").replace("uzs", "")
                .replace("%", "")
                .replace(" ", "").replace(" ", "").replace("_", "");
        if (s.isEmpty() || s.startsWith("-")) return Optional.empty();
        if (THOUSANDS.matcher(s).matches()) {
            s = s.replace(".", "").replace(",", "");
        } else {
            s = s.replace(',', '.');
        }
        if (!NUMBER.matcher(s).matches()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Foiz maydonlari uchun: minglik ajratgich QABUL QILINMAYDI, vergul va nuqta — o'nli kasr belgisi.
     * "12,5" → 12.5; "0,075" → 3 xonali kasr — rad (foiz 2 xonagacha). "1.000" → rad (foizda minglik yo'q).
     */
    public static Optional<BigDecimal> parsePercent(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim().replace("%", "").replace(" ", "").replace(" ", "").replace(',', '.');
        if (s.isEmpty() || !NUMBER.matcher(s).matches()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** "172 300" yoki "25 700,50" ko'rinishida. */
    public static String format(BigDecimal v) {
        if (v == null) return "—";
        BigDecimal s = scale(v);
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(' ');
        sym.setDecimalSeparator(',');
        String pattern = s.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0";
        DecimalFormat f = new DecimalFormat(pattern, sym);
        f.setRoundingMode(ROUNDING);
        return f.format(s);
    }

    /** Foiz ko'rinishi: "20", "14,86", "-20". */
    public static String formatPercent(BigDecimal v) {
        if (v == null) return "—";
        BigDecimal s = v.setScale(2, ROUNDING).stripTrailingZeros();
        if (s.scale() < 0) s = s.setScale(0);
        return s.toPlainString().replace('.', ',');
    }
}
