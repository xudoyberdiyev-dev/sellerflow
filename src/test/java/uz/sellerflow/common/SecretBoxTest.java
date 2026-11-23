package uz.sellerflow.common;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBoxTest {

    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTrip_randomIv_andAadBinding() {
        SecretBox b = new SecretBox(KEY);
        byte[] c1 = b.encrypt("secret-token-123", "business:1:UZUM");
        byte[] c2 = b.encrypt("secret-token-123", "business:1:UZUM");
        assertThat(c1).isNotEqualTo(c2);
        assertThat(new String(c1, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("secret-token");
        assertThat(b.decrypt(c1, "business:1:UZUM")).isEqualTo("secret-token-123");
        assertThatThrownBy(() -> b.decrypt(c1, "business:2:UZUM")).isInstanceOf(IllegalStateException.class);
        c1[c1.length - 1] ^= 1;
        assertThatThrownBy(() -> b.decrypt(c1, "business:1:UZUM")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledWithoutKey_andRejectsWrongLength() {
        assertThat(new SecretBox("").enabled()).isFalse();
        assertThatThrownBy(() -> new SecretBox("").encrypt("x", "a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretBox(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretBox("not base64 !!")).isInstanceOf(IllegalStateException.class);
    }
}
