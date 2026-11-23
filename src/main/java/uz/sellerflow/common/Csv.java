package uz.sellerflow.common;

/** CSV yordamchilari: formula-injection'dan himoya (=,+,-,@ bilan boshlangan qiymatga «'»), qo'shtirnoqlash. */
public final class Csv {
    private Csv() {}

    /** Excel UTF-8 ni to'g'ri ochishi uchun fayl boshidagi BOM. */
    public static final String BOM = "﻿";

    public static String cell(String v) {
        String s = v == null ? "" : v;
        String t = s.stripLeading();
        if (!s.isEmpty() && ("=+-@\t\r".indexOf(s.charAt(0)) >= 0 || (!t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0))) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Raqamli qiymat (bizning o'zimiz yaratgan BigDecimal satri) — himoyasiz, lekin faqat raqam/nuqta/minus bo'lsa. */
    public static String number(String v) {
        if (v == null || v.isEmpty()) return "";
        return v.matches("-?\\d+(\\.\\d+)?") ? v : cell(v);
    }

    public static String row(String... cells) {
        return String.join(",", cells) + "\r\n";
    }
}
