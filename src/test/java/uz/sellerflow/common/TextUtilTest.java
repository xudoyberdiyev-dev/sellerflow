package uz.sellerflow.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** M-14: surrogate juftini buzmasdan qisqartirish va 4096 limit bo'yicha bo'lish. */
class TextUtilTest {

    static boolean wellFormed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void truncate_neverSplitsSurrogatePair() {
        String name = "a".repeat(38) + "😀😀😀"; // emoji 39-40 pozitsiyada boshlanadi
        for (int max = 1; max <= name.length(); max++) {
            String t = TextUtil.truncate(name, max, "…");
            assertThat(wellFormed(t)).as("max=" + max).isTrue();
            assertThat(t.length()).isLessThanOrEqualTo(Math.max(max, 1));
        }
        assertThat(TextUtil.truncate("qisqa", 40, "…")).isEqualTo("qisqa");
        assertThat(TextUtil.truncate(null, 5)).isNull();
    }

    @Test
    void split_respectsLimit_lines_andSurrogates() {
        String line = "😀".repeat(30) + " <b>qator</b>\n";
        String big = line.repeat(200);
        List<String> parts = TextUtil.splitForTelegram(big, 4096);
        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts).allSatisfy(p -> {
            assertThat(p.length()).isLessThanOrEqualTo(4096);
            assertThat(wellFormed(p)).isTrue();
            assertThat(p).doesNotStartWith("\n");
        });
        assertThat(String.join("\n", parts).replace("\n", "")).isEqualTo(big.replace("\n", ""));
        // qatorsiz uzun matn
        List<String> noLines = TextUtil.splitForTelegram("😀".repeat(5000), 4096);
        assertThat(noLines).allSatisfy(p -> assertThat(wellFormed(p)).isTrue());
        assertThat(String.join("", noLines)).isEqualTo("😀".repeat(5000));
        assertThat(TextUtil.splitForTelegram("kichik", 4096)).containsExactly("kichik");
    }
}
