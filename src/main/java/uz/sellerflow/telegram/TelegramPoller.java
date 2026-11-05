package uz.sellerflow.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.sellerflow.bot.BotDispatcher;
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

    private final HttpTelegramGateway gateway;
    private final BotDispatcher dispatcher;
    private final SellerFlowProperties props;
    private final BotStatus status;
    private volatile boolean running = true;

    public TelegramPoller(HttpTelegramGateway gateway, BotDispatcher dispatcher, SellerFlowProperties props,
                          BotStatus status) {
        this.gateway = gateway;
        this.dispatcher = dispatcher;
        this.props = props;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        gateway.deleteWebhook();
        gateway.setCommands(List.of(
                Map.of("command", "start", "description", "Boshlash"),
                Map.of("command", "menu", "description", "Bosh menyu"),
                Map.of("command", "help", "description", "Yordam"),
                Map.of("command", "settings", "description", "Sozlamalar"),
                Map.of("command", "cancel", "description", "Joriy jarayonni bekor qilish")));
        Thread t = new Thread(this::loop, "telegram-poller");
        t.setDaemon(true);
        t.start();
        status.started();
        log.info("Telegram long polling started");
    }

    private void loop() {
        long offset = 0;
        int backoffMs = 1000;
        while (running) {
            try {
                List<Tg.Update> updates = gateway.getUpdates(offset, props.telegram().pollingTimeoutSeconds());
                status.pollOk();
                for (Tg.Update u : updates) {
                    try {
                        dispatcher.handle(u);
                        status.processed();
                    } catch (RuntimeException e) {
                        status.error("update handling: " + e.getClass().getSimpleName());
                        log.error("Update {} handling failed", u.updateId(), e);
                    }
                    offset = Math.max(offset, u.updateId() + 1);
                }
                backoffMs = 1000;
            } catch (RuntimeException e) {
                status.error("polling: " + e.getClass().getSimpleName());
                log.warn("Polling error: {}; retry in {} ms", e.getClass().getSimpleName(), backoffMs);
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 30_000);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() { running = false; }
}
