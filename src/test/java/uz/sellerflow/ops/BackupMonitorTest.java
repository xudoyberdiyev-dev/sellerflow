package uz.sellerflow.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;
import uz.sellerflow.support.FakeTelegram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** H-2: backup holati health va Telegram alert'da aks etadi. */
class BackupMonitorTest {

    @TempDir Path dir;
    final FakeTelegram tg = new FakeTelegram();
    final Instant now = Instant.parse("2026-11-07T12:00:00Z");

    private BackupMonitor monitor(String chat) {
        BackupMonitor m = new BackupMonitor(dir.resolve("status.json").toString(), 26, chat, tg, new ObjectMapper());
        m.setClock(Clock.fixed(now, ZoneOffset.UTC));
        return m;
    }

    private void status(String lastSuccessAt, int failures, String error) throws Exception {
        Files.writeString(dir.resolve("status.json"), """
                {"lastSuccessAt": %s, "lastSuccessFile": null, "lastFailureAt": %s, "lastError": %s, "consecutiveFailures": %d}
                """.formatted(lastSuccessAt == null ? "null" : "\"" + lastSuccessAt + "\"",
                failures > 0 ? "\"" + now.minusSeconds(60) + "\"" : "null",
                error == null ? "null" : "\"" + error + "\"", failures));
    }

    @Test
    void missingFile_isUnknown_andHealthUp() {
        BackupMonitor m = monitor("");
        assertThat(m.read().state()).isEqualTo(BackupMonitor.State.UNKNOWN);
        assertThat(m.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void freshSuccess_isOk() throws Exception {
        status(now.minus(Duration.ofHours(2)).toString(), 0, null);
        BackupMonitor m = monitor("");
        assertThat(m.read().state()).isEqualTo(BackupMonitor.State.OK);
        assertThat(m.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void oldSuccess_isStale_andHealthDown() throws Exception {
        status(now.minus(Duration.ofHours(27)).toString(), 0, null);
        BackupMonitor m = monitor("");
        assertThat(m.read().state()).isEqualTo(BackupMonitor.State.STALE);
        assertThat(m.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void failure_alertsOnce_perIncident() throws Exception {
        status(now.minus(Duration.ofHours(3)).toString(), 2, "pg_dump: connection <refused>");
        BackupMonitor m = monitor("-1001234");
        assertThat(m.read().state()).isEqualTo(BackupMonitor.State.FAILED);
        assertThat(m.health().getStatus()).isEqualTo(Status.DOWN);
        m.checkAndAlert();
        m.checkAndAlert();
        assertThat(tg.forChat(-1001234L)).hasSize(1);
        assertThat(tg.last(-1001234L).text()).contains("backup xatosi").contains("&lt;refused&gt;").contains("Ketma-ket xatolar: 2");
    }

    @Test
    void noChatId_noAlert() throws Exception {
        status(null, 1, "x");
        monitor("").checkAndAlert();
        assertThat(tg.forChat(0)).isEmpty();
    }
}
