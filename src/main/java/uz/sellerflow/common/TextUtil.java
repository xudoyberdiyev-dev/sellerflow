package uz.sellerflow.common;

import java.util.ArrayList;
import java.util.List;

/** Matn yordamchilari: Unicode (surrogate juftlari) xavfsiz qisqartirish va Telegram limiti (M-14). */
public final class TextUtil {
    private TextUtil() {}

    /** Telegram sendMessage matn limiti (UTF-16 birliklarida). */
    public static final int TELEGRAM_LIMIT = 4096;

    /**
     * Ko'pi bilan {@code max} UTF-16 birligigacha qisqartiradi, surrogate juftini (emoji) o'rtasidan bo'lmaydi.
     * Qisqartirilsa oxiriga {@code suffix} qo'shiladi (natija uzunligi suffix bilan birga ≤ max).
     */
    public static String truncate(String s, int max, String suffix) {
        if (s == null || s.length() <= max) return s;
        String sfx = suffix == null ? "" : suffix;
        int end = Math.max(0, max - sfx.length());
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + sfx;
    }

    public static String truncate(String s, int max) {
        return truncate(s, max, "");
    }

    /**
     * Uzun xabarni Telegram limitiga mos bo'laklarga ajratadi: avval qator chegarasidan, bo'lmasa
     * surrogate-xavfsiz joydan. HTML teglar odatda bir qator ichida bo'lgani uchun qator bo'yicha bo'linadi.
     */
    /**
     * Telegram 4096 limiti bo'yicha bo'lish (M-14). LOW: HTML-xavfsiz — teg ({@code <b ...>}) yoki entity
     * ({@code &amp;}) o'rtasidan kesilmaydi; bo'lak oxirida ochiq qolgan teglar yopiladi va keyingi bo'lakda qayta ochiladi.
     */
    public static List<String> splitForTelegram(String text, int limit) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        final int reserve = Math.min(200, limit / 4); // yopuvchi/qayta ochuvchi teglar uchun joy
        String prefix = "";
        String rest = text;
        while (prefix.length() + rest.length() > limit) {
            int budget = limit - reserve - prefix.length();
            int cut = rest.lastIndexOf('\n', budget);
            if (cut <= budget / 2) {
                cut = budget;
                if (Character.isHighSurrogate(rest.charAt(cut - 1))) cut--;
            }
            cut = safeCut(rest, cut);
            String chunk = prefix + rest.substring(0, cut);
            List<String> open = openTags(chunk);
            StringBuilder close = new StringBuilder();
            for (int k = open.size() - 1; k >= 0; k--) close.append("</").append(tagName(open.get(k))).append('>');
            out.add(chunk + close);
            prefix = String.join("", open);
            rest = rest.substring(cut);
            if (rest.startsWith("\n")) rest = rest.substring(1);
        }
        out.add(prefix + rest);
        return out;
    }

    /** Kesish nuqtasi teg yoki entity ichida bo'lsa — uning boshiga suriladi. */
    static int safeCut(String s, int cut) {
        int lt = s.lastIndexOf('<', cut - 1);
        if (lt >= 0 && s.indexOf('>', lt) >= cut && lt > 0) cut = lt;
        int amp = s.lastIndexOf('&', cut - 1);
        if (amp >= 0 && cut - amp <= 10) {
            int semi = s.indexOf(';', amp);
            if (semi >= cut && amp > 0) cut = amp;
        }
        return cut;
    }

    private static final java.util.regex.Pattern TAG = java.util.regex.Pattern.compile("<(/?)([a-zA-Z-]+)[^>]*>");

    /** Matnda ochiq qolgan teglar (ochilish tartibida, to'liq ochuvchi teg matni bilan). */
    static List<String> openTags(String html) {
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        var m = TAG.matcher(html);
        while (m.find()) {
            String name = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (m.group(1).isEmpty()) {
                stack.push(m.group(0));
            } else {
                // eng yaqin mos ochuvchi tegni olib tashlash
                for (var it = stack.iterator(); it.hasNext(); ) {
                    if (tagName(it.next()).equals(name)) { it.remove(); break; }
                }
            }
        }
        List<String> open = new ArrayList<>(stack);
        java.util.Collections.reverse(open);
        return open;
    }

    private static String tagName(String openTag) {
        var m = TAG.matcher(openTag);
        return m.find() ? m.group(2).toLowerCase(java.util.Locale.ROOT) : "";
    }

}
