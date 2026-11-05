package uz.sellerflow.telegram;

import org.springframework.stereotype.Component;

import java.time.Instant;

/** Bot holati (admin panel "Tizim holati" uchun). */
@Component
public class BotStatus {
    private volatile boolean pollingEnabled;
    private volatile Instant lastPollOkAt;
    private volatile Instant lastErrorAt;
    private volatile String lastError;
    private volatile long processedUpdates;

    public void started() { pollingEnabled = true; }
    public void pollOk() { lastPollOkAt = Instant.now(); }
    public void processed() { processedUpdates++; }
    public void error(String what) {
        lastErrorAt = Instant.now();
        lastError = what;
    }

    public boolean isPollingEnabled() { return pollingEnabled; }
    public Instant getLastPollOkAt() { return lastPollOkAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }
    public long getProcessedUpdates() { return processedUpdates; }
}
