package uz.sellerflow.account;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** N-1: Windows (CRLF) checkout'da shartlar xeshi LF bilan bir xil bo'lishi kerak. */
class TermsGuardTest {

    @Test
    void hash_isIndependentOfLineEndings() throws Exception {
        String lf = new String(new ClassPathResource("bot/terms_uz.txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String crlf = lf.replace("\r\n", "\n").replace("\n", "\r\n");
        String cr = lf.replace("\r\n", "\n").replace("\n", "\r");
        String expected = TermsGuard.sha256(TermsGuard.normalize(lf));
        assertThat(TermsGuard.sha256(TermsGuard.normalize(crlf))).isEqualTo(expected);
        assertThat(TermsGuard.sha256(TermsGuard.normalize(cr))).isEqualTo(expected);
        assertThat(TermsGuard.sha256(TermsGuard.normalize("\uFEFF" + crlf))).isEqualTo(expected); // BOM ham hisobga olinmaydi
        assertThat(TermsGuard.normalize("a\r\nb\r\n\r\n")).isEqualTo("a\nb");
    }
}
