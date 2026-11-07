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
    public static List<String> splitForTelegram(String text, int limit) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String rest = text;
        while (rest.length() > limit) {
            int cut = rest.lastIndexOf('\n', limit);
            if (cut <= limit / 2) {
                cut = limit;
                if (Character.isHighSurrogate(rest.charAt(cut - 1))) cut--;
            }
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut);
            if (rest.startsWith("\n")) rest = rest.substring(1);
        }
        out.add(rest);
        return out;
    }
}
