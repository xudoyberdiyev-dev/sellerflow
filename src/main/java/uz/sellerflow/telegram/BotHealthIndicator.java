package uz.sellerflow.telegram;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * M-12: bot holati /actuator/health da (indikator "bot"). Poller oxirgi muvaffaqiyatli getUpdates'dan
 * {@link #MAX_SILENCE} dan ko'p vaqt o'tsa — DOWN (masalan, token bekor qilingan, 401/409, tarmoq yo'q).
 * Polling o'chirilgan (token yo'q) bo'lsa — UP, detail "disabled".
 */
@Component("bot")
public class BotHealthIndicator implements HealthIndicator {

    static final Duration MAX_SILENCE = Duration.ofSeconds(120);

    private final BotStatus status;
    private Clock clock = Clock.systemUTC();

    public BotHealthIndicator(BotStatus status) {
        this.status = status;
    }

    void setClock(Clock clock) { this.clock = clock; }

    @Override
    public Health health() {
        if (!status.isPollingEnabled()) return Health.up().withDetail("polling", "disabled").build();
        Instant now = clock.instant();
        Instant last = status.getLastPollOkAt();
        Instant since = last != null ? last : status.getStartedAt();
        if (since == null || since.isAfter(now.minus(MAX_SILENCE))) return Health.up().build();
        return Health.down().withDetail("polling", "stale").build();
    }
}
