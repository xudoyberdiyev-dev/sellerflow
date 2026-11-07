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
 * Fayl yo'q bo'lsa (masalan, lokal ishlab chiqish) — UNKNOWN, health'ga ta'sir qilmaydi.
 */
@Component("backup")
public class BackupMonitor implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BackupMonitor.class);
    static final Duration REALERT = Duration.ofHours(24);

    public enum State { OK, FAILED, STALE, UNKNOWN }

    public record Status(State state, Instant lastSuccessAt, String lastSuccessFile, Instant lastFailureAt,
                         String lastError, int consecutiveFailures) {}

    private final Path file;
    private final Duration maxAge;
    private final String alertChatId;
    private final TelegramGateway tg;
    private final ObjectMapper mapper;
    private Clock clock = Clock.systemUTC();
    private String lastAlertKey;
    private Instant lastAlertAt;

    public BackupMonitor(@Value("${sellerflow.backup.status-file:/backups/status.json}") String file,
                         @Value("${sellerflow.backup.max-age-hours:26}") long maxAgeHours,
                         @Value("${sellerflow.backup.alert-chat-id:}") String alertChatId,
                         TelegramGateway tg, ObjectMapper mapper) {
        this.file = Path.of(file);
        this.maxAge = Duration.ofHours(maxAgeHours);
        this.alertChatId = alertChatId == null ? "" : alertChatId.strip();
        this.tg = tg;
        this.mapper = mapper;
    }

    void setClock(Clock clock) { this.clock = clock; }

    public Status read() {
        if (!Files.isRegularFile(file)) return new Status(State.UNKNOWN, null, null, null, null, 0);
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
            case FAILED, STALE -> Health.down();
        };
        return b.withDetail("state", s.state().name()).build();
    }

    /** Har 30 daqiqada: muammo bo'lsa ogohlantirish (bir xil muammo uchun 24 soatda bir marta). */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    public void checkAndAlert() {
        if (alertChatId.isEmpty()) return;
        Status s = read();
        if (s.state() != State.FAILED && s.state() != State.STALE) {
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
        String text = (s.state() == State.FAILED ? "⚠️ <b>SellerFlow: backup xatosi</b>" : "⚠️ <b>SellerFlow: backup eskirgan</b>")
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
