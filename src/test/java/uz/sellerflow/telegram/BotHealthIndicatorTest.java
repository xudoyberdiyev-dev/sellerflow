package uz.sellerflow.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** M-12: bot health poller'ning oxirgi muvaffaqiyatini aks ettiradi. */
class BotHealthIndicatorTest {

    @Test
    void disabledPolling_isUp() {
        assertThat(new BotHealthIndicator(new BotStatus()).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void recentPoll_isUp_stalePoll_isDown() {
        BotStatus s = new BotStatus();
        s.started();
        s.pollOk();
        BotHealthIndicator h = new BotHealthIndicator(s);
        assertThat(h.health().getStatus()).isEqualTo(Status.UP);
        h.setClock(Clock.fixed(Instant.now().plus(Duration.ofSeconds(121)), ZoneOffset.UTC));
        assertThat(h.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void neverSucceeded_isUpDuringGrace_thenDown() {
        BotStatus s = new BotStatus();
        s.started();
        BotHealthIndicator h = new BotHealthIndicator(s);
        assertThat(h.health().getStatus()).isEqualTo(Status.UP);
        h.setClock(Clock.fixed(Instant.now().plus(Duration.ofMinutes(3)), ZoneOffset.UTC));
        assertThat(h.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
