package uz.sellerflow.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.sellerflow.telegram.TelegramGateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * H-2: backup holati — backup servisi yozadigan {@code status.json} dan o'qiladi.
 * Admin /health va /actuator/health (indikator "backup") da ko'rinadi; ALERT_TELEGRAM_CHAT_ID berilsa,
 * backup xato bersa yoki eskirsa (oxirgi muvaffaqiyat > max-age) bot orqali ogohlantirish yuboriladi.
 * N-2: fayl yo'q bo'lsa — {@code sellerflow.backup.required=false} (lokal ishlab chiqish) da UNKNOWN; production'da
 * (compose: {@code BACKUP_REQUIRED=true}) ilova ishga tushganidan {@code missing-grace-hours} o'tib ham fayl bo'lmasa —
 * MISSING (health DOWN + alert): backup konteyneri umuman ishlamayapti degani.
 * N-3: alert chat sozlanmagan bo'lsa startupda WARN.
 */
@Component("backup")
public class BackupMonitor implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BackupMonitor.class);
    static final Duration REALERT = Duration.ofHours(24);

    public enum State { OK, FAILED, STALE, MISSING, UNKNOWN }

    public record Status(State state, Instant lastSuccessAt, String lastSuccessFile, Instant lastFailureAt,
                         String lastError, int consecutiveFailures) {}

    private final Path file;
    private final Duration maxAge;
    private final String alertChatId;
    private final TelegramGateway tg;
    private final ObjectMapper mapper;
    private final boolean required;
    private final Duration missingGrace;
    private Clock clock = Clock.systemUTC();
    private Instant startedAt;
    private String lastAlertKey;
    private Instant lastAlertAt;

    public BackupMonitor(@Value("${sellerflow.backup.status-file:/backups/status.json}") String file,
                         @Value("${sellerflow.backup.max-age-hours:26}") long maxAgeHours,
                         @Value("${sellerflow.backup.alert-chat-id:}") String alertChatId,
                         @Value("${sellerflow.backup.required:false}") boolean required,
                         @Value("${sellerflow.backup.missing-grace-hours:2}") long missingGraceHours,
                         TelegramGateway tg, ObjectMapper mapper) {
        this.required = required;
        this.missingGrace = Duration.ofHours(missingGraceHours);
        this.startedAt = Instant.now();
        this.file = Path.of(file);
        this.maxAge = Duration.ofHours(maxAgeHours);
        this.alertChatId = alertChatId == null ? "" : alertChatId.strip();
        this.tg = tg;
        this.mapper = mapper;
    }

    void setClock(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /** Test uchun: soatni o'zgartiradi, ishga tushish vaqtini saqlaydi. */
    void setClockKeepStart(Clock clock) {
        this.clock = clock;
    }

    @jakarta.annotation.PostConstruct
    void warnIfUnconfigured() {
        if (alertChatId.isEmpty()) {
            log.warn("ALERT_TELEGRAM_CHAT_ID is not set — backup failures will NOT be reported to Telegram (see docs/OPERATIONS.md §5)");
        }
        if (!required) log.info("BACKUP_REQUIRED=false — missing backup status is not treated as a failure");
    }

    public Status read() {
        if (!Files.isRegularFile(file)) {
            if (required && !clock.instant().isBefore(startedAt.plus(missingGrace))) {
                return new Status(State.MISSING, null, null, null,
                        "status.json topilmadi — backup servisi ishlamayapti", 0);
            }
            return new Status(State.UNKNOWN, null, null, null, null, 0);
        }
        try {
            JsonNode n = mapper.readTree(Files.readString(file));
            Instant ok = instant(n, "lastSuccessAt");
            Instant failedAt = instant(n, "lastFailureAt");
            int failures = n.path("consecutiveFailures").asInt(0);
            String err = n.path("lastError").isTextual() ? n.path("lastError").asText() : null;
            String okFile = n.path("lastSuccessFile").isTextual() ? n.path("lastSuccessFile").asText() : null;
            State st = failures > 0 ? State.FAILED
                    : ok == null || ok.isBefore(clock.instant().minus(maxAge)) ? State.STALE
                    : State.OK;
            return new Status(st, ok, okFile, failedAt, err, failures);
        } catch (Exception e) {
            return new Status(State.FAILED, null, null, null, "status.json o'qilmadi: " + e.getClass().getSimpleName(), 0);
        }
    }

    @Override
    public Health health() {
        Status s = read();
        Health.Builder b = switch (s.state()) {
            case OK, UNKNOWN -> Health.up();
            case FAILED, STALE, MISSING -> Health.down();
        };
        return b.withDetail("state", s.state().name()).build();
    }

    /** Har 30 daqiqada: muammo bo'lsa ogohlantirish (bir xil muammo uchun 24 soatda bir marta). */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    public void checkAndAlert() {
        if (alertChatId.isEmpty()) return;
        Status s = read();
        if (s.state() != State.FAILED && s.state() != State.STALE && s.state() != State.MISSING) {
            lastAlertKey = null;
            return;
        }
        String key = s.state() + "|" + s.lastFailureAt() + "|" + s.lastSuccessAt();
        Instant now = clock.instant();
        if (key.equals(lastAlertKey) && lastAlertAt != null && now.isBefore(lastAlertAt.plus(REALERT))) return;
        long chat;
        try {
            chat = Long.parseLong(alertChatId);
        } catch (NumberFormatException e) {
            log.warn("ALERT_TELEGRAM_CHAT_ID is not a number — backup alert skipped");
            return;
        }
        String text = (s.state() == State.FAILED ? "⚠️ <b>SellerFlow: backup xatosi</b>"
                : s.state() == State.MISSING ? "⚠️ <b>SellerFlow: backup ishlamayapti</b>" : "⚠️ <b>SellerFlow: backup eskirgan</b>")
                + "\n\nOxirgi muvaffaqiyatli backup: " + (s.lastSuccessAt() == null ? "yo'q" : s.lastSuccessAt())
                + (s.consecutiveFailures() > 0 ? "\nKetma-ket xatolar: " + s.consecutiveFailures() : "")
                + (s.lastError() == null ? "" : "\nXato: " + esc(s.lastError()))
                + "\n\nTekshiring: <code>docker compose logs backup | tail</code>";
        try {
            tg.sendMessage(chat, text, null);
            lastAlertKey = key;
            lastAlertAt = now;
        } catch (RuntimeException e) {
            log.warn("Backup alert could not be sent: {}", e.getClass().getSimpleName());
        }
    }

    private static Instant instant(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isTextual() && !v.asText().isBlank() ? Instant.parse(v.asText()) : null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
