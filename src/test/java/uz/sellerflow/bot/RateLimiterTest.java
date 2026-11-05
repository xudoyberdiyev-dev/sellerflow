package uz.sellerflow.bot;

import org.junit.jupiter.api.Test;
import uz.sellerflow.admin.Totp;
import uz.sellerflow.common.SellerFlowProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void limitsPerUser_warnsOnceThenDrops() {
        RateLimiter rl = new RateLimiter(new SellerFlowProperties(null, null, null, null, null, null,
                new SellerFlowProperties.RateLimit(3, 10)));
        assertThat(rl.check(1)).isEqualTo(RateLimiter.Verdict.OK);
        assertThat(rl.check(1)).isEqualTo(RateLimiter.Verdict.OK);
        assertThat(rl.check(1)).isEqualTo(RateLimiter.Verdict.OK);
        assertThat(rl.check(1)).isEqualTo(RateLimiter.Verdict.WARN);
        assertThat(rl.check(1)).isEqualTo(RateLimiter.Verdict.DROP);
        assertThat(rl.check(2)).isEqualTo(RateLimiter.Verdict.OK);
    }

    @Test
    void totp_rfc6238TestVector() {
        // RFC 6238 ilova B: kalit "12345678901234567890", T=59s -> 94287082 (8 raqam) => 6 raqam: 287082
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(Totp.code(secret, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.verify(secret, "287082", 59)).isTrue();
        assertThat(Totp.verify(secret, "000000", 59)).isFalse();
    }
}
