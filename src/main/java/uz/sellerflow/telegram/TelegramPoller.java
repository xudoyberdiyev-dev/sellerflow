package uz.sellerflow.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.sellerflow.bot.BotDispatcher;
import uz.sellerflow.common.Infra;
import uz.sellerflow.common.SellerFlowProperties;

import java.util.List;
import java.util.Map;

/**
 * Long polling. Update'lar bitta oqimda, ketma-ket qayta ishlanadi — bitta foydalanuvchining
 * parallel callback'lari holatni buzmasligi uchun eng oddiy kafolat.
 */
@Component
@ConditionalOnExpression("'${sellerflow.telegram.bot-token:}' != '' and '${sellerflow.telegram.polling-enabled:true}' == 'true'")
public class TelegramPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);

    private final HttpTelegramGateway http;
    private final TelegramGateway gateway;
    private final UpdateHandler dispatcher;
    private final int timeoutSeconds;
    private final BotStatus status;
    private volatile boolean running = true;
    private volatile Thread thread;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramPoller(HttpTelegramGateway gateway, BotDispatcher dispatcher, SellerFlowProperties props,
                          BotStatus status) {
        this(gateway, gateway, dispatcher, props.telegram().pollingTimeoutSeconds(), status);
    }

    /** Testlar uchun: soxta gateway va handler bilan. */
    TelegramPoller(HttpTelegramGateway http, TelegramGateway gateway, UpdateHandler dispatcher, int timeoutSeconds,
                   BotStatus status) {
        this.http = http;
        this.gateway = gateway;
        this.dispatcher = dispatcher;
        this.timeoutSeconds = timeoutSeconds;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        http.deleteWebhook();
        http.setCommands(List.of(
                Map.of("command", "start", "description", "Boshlash"),
                Map.of("command", "menu", "description", "Bosh menyu"),
                Map.of("command", "help", "description", "Yordam"),
                Map.of("command", "settings", "description", "Sozlamalar"),
                Map.of("command", "cancel", "description", "Joriy jarayonni bekor qilish")));
        Thread t = new Thread(this::loop, "telegram-poller");
        t.setDaemon(true);
        thread = t;
        t.start();
        status.started();
        log.info("Telegram long polling started");
    }

    private void loop() {
        long offset = 0;
        int backoffMs = 1000;
        while (running) {
            try {
                offset = pollOnce(offset);
                backoffMs = 1000;
            } catch (RuntimeException e) {
                status.error("polling: " + e.getClass().getSimpleName());
                log.warn("Polling error: {}; retry in {} ms", e.getClass().getSimpleName(), backoffMs);
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 30_000);
            }
        }
        log.info("Telegram long polling stopped");
    }

    /**
     * Bitta getUpdates sikli. Qaytaradi: keyingi offset. M-10: update vaqtinchalik infratuzilma xatosi
     * (baza uzilgan) bilan tugasa — offset shu update'dan oldinga SURILMAYDI va xato yuqoriga uzatiladi
     * (backoff, so'ng Telegram shu update'larni qayta beradi). Boshqa xatolar — log va keyingi update.
     */
    long pollOnce(long offset) {
        List<Tg.Update> updates = gateway.getUpdates(offset, timeoutSeconds);
        status.pollOk();
        for (Tg.Update u : updates) {
            if (!running) break;
            try {
                dispatcher.handle(u);
                status.processed();
            } catch (RuntimeException e) {
                if (Infra.isTransient(e)) {
                    status.error("update handling (will retry): " + e.getClass().getSimpleName());
                    log.warn("Update {} deferred — transient failure: {}", u.updateId(), e.getClass().getSimpleName());
                    throw e;
                }
                status.error("update handling: " + e.getClass().getSimpleName());
                log.error("Update {} handling failed", u.updateId(), e);
            }
            offset = Math.max(offset, u.updateId() + 1);
        }
        return offset;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Graceful shutdown: yangi update olinmaydi, joriy update tugashi kutiladi (ko'pi bilan 10 s). */
    @jakarta.annotation.PreDestroy
    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
