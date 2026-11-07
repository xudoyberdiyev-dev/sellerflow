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

import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Long polling. M-9: bitta getUpdates partiyasi foydalanuvchi bo'yicha bo'linadi ({@code userId % workers}) va
 * {@code sellerflow.telegram.workers} ta oqimda parallel qayta ishlanadi. Bitta foydalanuvchining update'lari
 * doim bitta oqimda, kelgan tartibda — holat buzilmaydi; sekin foydalanuvchi boshqalarni to'xtatmaydi.
 * Offset faqat butun partiya tugagach suriladi.
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
    private final int workers;
    private final ExecutorService pool;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramPoller(HttpTelegramGateway gateway, BotDispatcher dispatcher, SellerFlowProperties props,
                          BotStatus status, @Value("${sellerflow.telegram.workers:4}") int workers) {
        this(gateway, gateway, dispatcher, props.telegram().pollingTimeoutSeconds(), status, workers);
    }

    /** Testlar uchun: soxta gateway va handler bilan. */
    TelegramPoller(HttpTelegramGateway http, TelegramGateway gateway, UpdateHandler dispatcher, int timeoutSeconds,
                   BotStatus status, int workers) {
        this.http = http;
        this.gateway = gateway;
        this.dispatcher = dispatcher;
        this.timeoutSeconds = timeoutSeconds;
        this.status = status;
        this.workers = Math.max(1, workers);
        AtomicInteger n = new AtomicInteger();
        this.pool = this.workers == 1 ? null : Executors.newFixedThreadPool(this.workers, r -> {
            Thread t = new Thread(r, "telegram-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
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
                backoffMs = nextBackoff(backoffMs);
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
        if (updates.isEmpty() || !running) return offset;

        // foydalanuvchi bo'yicha bo'lish (tartib saqlanadi)
        Map<Integer, List<Tg.Update>> parts = new LinkedHashMap<>();
        for (Tg.Update u : updates) {
            parts.computeIfAbsent(Math.floorMod(userKey(u), workers), k -> new ArrayList<>()).add(u);
        }
        RuntimeException transientFailure = null;
        if (pool == null || parts.size() == 1) {
            for (List<Tg.Update> part : parts.values()) {
                RuntimeException f = runPartition(part);
                if (f != null && transientFailure == null) transientFailure = f;
            }
        } else {
            List<Future<RuntimeException>> futures = new ArrayList<>();
            for (List<Tg.Update> part : parts.values()) futures.add(pool.submit(() -> runPartition(part)));
            for (Future<RuntimeException> f : futures) {
                try {
                    RuntimeException r = f.get();
                    if (r != null && transientFailure == null) transientFailure = r;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("worker failed", e.getCause());
                }
            }
        }
        if (transientFailure != null) throw transientFailure; // offset surilmaydi — partiya qayta keladi
        long next = offset;
        for (Tg.Update u : updates) next = Math.max(next, u.updateId() + 1);
        return next;
    }

    /** Bitta foydalanuvchi guruhi ketma-ket. Vaqtinchalik xatoda shu guruhning qolgani to'xtatiladi (tartib uchun). */
    private RuntimeException runPartition(List<Tg.Update> part) {
        for (Tg.Update u : part) {
            if (!running) return null;
            try {
                dispatcher.handle(u);
                status.processed();
            } catch (RuntimeException e) {
                if (Infra.isTransient(e)) {
                    status.error("update handling (will retry): " + e.getClass().getSimpleName());
                    log.warn("Update {} deferred — transient failure: {}", u.updateId(), e.getClass().getSimpleName());
                    return e;
                }
                status.error("update handling: " + e.getClass().getSimpleName());
                log.error("Update {} handling failed", u.updateId(), e);
            }
        }
        return null;
    }

    /** Eksponensial backoff: 1 s → 2 → 4 … ko'pi bilan 30 s. */
    static int nextBackoff(int currentMs) {
        return Math.min(Math.max(currentMs, 1000) * 2, 30_000);
    }

    static long userKey(Tg.Update u) {
        if (u.message() != null && u.message().from() != null) return u.message().from().id();
        if (u.callbackQuery() != null && u.callbackQuery().from() != null) return u.callbackQuery().from().id();
        return u.updateId();
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
        if (pool != null) pool.shutdown();
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
