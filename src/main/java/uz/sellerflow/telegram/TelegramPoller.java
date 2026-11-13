package uz.sellerflow.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.sellerflow.bot.BotDispatcher;
import uz.sellerflow.common.Infra;
import uz.sellerflow.common.SellerFlowProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * Long polling.
 * <ul>
 *   <li><b>N-12:</b> har bir worker'ning doimiy navbati bor; update foydalanuvchi bo'yicha
 *       ({@code userId % workers}) navbatga qo'yiladi. Partiyalar bir-birini kutmaydi — sekin foydalanuvchi
 *       faqat o'z worker'ini band qiladi, poller esa yangi update'larni olishda davom etadi. Bitta foydalanuvchining
 *       update'lari doim bitta navbatda, kelgan tartibda.</li>
 *   <li><b>At-least-once (M-10):</b> Telegram offset'i faqat eng kichik hali tugamagan update'gacha suriladi —
 *       ilova o'chib qolsa, tugamagan update'lar qayta keladi (dublikatni {@code processed_updates} o'tkazadi).</li>
 *   <li><b>N-7:</b> vaqtinchalik (infratuzilma) xato — {@link #TRANSIENT_RETRY_WINDOW} davomida eksponensial kutish bilan
 *       qayta urinish; undan keyin va boshqa xatolarda — dead-letter (ERROR log + holat), cheksiz aylanish yo'q.
 *       Poller yoki worker oqimi o'lsa — watchdog qayta ishga tushiradi; OOM'da JVM chiqadi
 *       ({@code -XX:+ExitOnOutOfMemoryError}, Docker qayta ishga tushiradi).</li>
 * </ul>
 */
@Component
@ConditionalOnExpression("'${sellerflow.telegram.bot-token:}' != '' and '${sellerflow.telegram.polling-enabled:true}' == 'true'")
public class TelegramPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);
    static final Duration TRANSIENT_RETRY_WINDOW = Duration.ofMinutes(15);
    static final long IDLE_REPOLL_MS = 500;

    private final HttpTelegramGateway http;
    private final TelegramGateway gateway;
    private final UpdateHandler dispatcher;
    private final int timeoutSeconds;
    private final BotStatus status;
    private final int workers;
    private final List<BlockingQueue<Tg.Update>> queues = new ArrayList<>();
    private final Thread[] workerThreads;
    /** Navbatga qo'yilgan, lekin hali tugamagan update ID'lari (offset shulardan eng kichigidan oshmaydi). */
    private final ConcurrentSkipListSet<Long> inFlight = new ConcurrentSkipListSet<>();
    /** Offset'dan katta, lekin allaqachon tugagan ID'lar — Telegram ularni qayta bersa navbatga qo'yilmaydi. */
    private final ConcurrentSkipListSet<Long> done = new ConcurrentSkipListSet<>();
    private volatile boolean running = true;
    private volatile Thread thread;
    private volatile long maxSeen = -1;
    private volatile long offset;
    private volatile Duration retryWindow = TRANSIENT_RETRY_WINDOW;
    private LongConsumer sleeper = TelegramPoller::sleep;
    private PollingLock lock;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLock(PollingLock lock) { this.lock = lock; }

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
        this.workerThreads = new Thread[this.workers];
        for (int i = 0; i < this.workers; i++) {
            queues.add(new LinkedBlockingQueue<>());
            startWorker(i);
        }
    }

    /** Testlar uchun: kutishlarni almashtirish va qayta urinish oynasini qisqartirish. */
    void setSleeper(LongConsumer sleeper) { this.sleeper = sleeper; }

    void setRetryWindow(Duration d) { this.retryWindow = d; }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        http.deleteWebhook();
        http.setCommands(List.of(
                Map.of("command", "start", "description", "Boshlash"),
                Map.of("command", "menu", "description", "Bosh menyu"),
                Map.of("command", "help", "description", "Yordam"),
                Map.of("command", "settings", "description", "Sozlamalar"),
                Map.of("command", "cancel", "description", "Joriy jarayonni bekor qilish")));
        startPollerThread();
        status.started();
        log.info("Telegram long polling started");
    }

    private synchronized void startPollerThread() {
        Thread t = new Thread(this::loop, "telegram-poller");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    private synchronized void startWorker(int i) {
        Thread t = new Thread(() -> workLoop(i), "telegram-worker-" + (i + 1));
        t.setDaemon(true);
        workerThreads[i] = t;
        t.start();
    }

    /** N-7: oqim kutilmaganda o'lgan bo'lsa (Error) — qayta ishga tushiriladi. */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT60S")
    public void watchdog() {
        if (!running) return;
        Thread p = thread;
        if (p != null && !p.isAlive()) {
            log.error("Telegram poller thread died — restarting");
            status.error("poller thread restarted");
            startPollerThread();
        }
        for (int i = 0; i < workers; i++) {
            if (!workerThreads[i].isAlive()) {
                log.error("Telegram worker {} died — restarting", i + 1);
                status.error("worker thread restarted");
                startWorker(i);
            }
        }
    }

    private void loop() {
        int backoffMs = 1000;
        while (running) {
            try {
                if (lock != null && !lock.ensureHeld()) {
                    status.error("polling lock held by another instance");
                    sleeper.accept(30_000);
                    continue;
                }
                offset = pollOnce(offset);
                backoffMs = 1000;
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable e) {
                // LOW (hujjat): HTTP status logda ko'rinsin — 401 (token noto'g'ri), 409 (boshqa nusxa ishlayapti)
                String what = e instanceof org.springframework.web.client.RestClientResponseException re
                        ? "HTTP " + re.getStatusCode().value() : e.getClass().getSimpleName();
                status.error("polling: " + what);
                log.warn("Telegram getUpdates failed: {}; retry in {} ms", what, backoffMs);
                sleeper.accept(backoffMs);
                backoffMs = nextBackoff(backoffMs);
            }
        }
        log.info("Telegram long polling stopped");
    }

    /**
     * Bitta getUpdates sikli: yangi update'larni worker navbatlariga qo'yadi (kutmaydi). Qaytaradi: keyingi offset —
     * tugamagan update bo'lsa, eng kichigi; aks holda ko'rilgan eng katta ID + 1.
     */
    long pollOnce(long offset) {
        List<Tg.Update> updates = gateway.getUpdates(offset, timeoutSeconds);
        status.pollOk();
        int fresh = 0;
        for (Tg.Update u : updates) {
            long id = u.updateId();
            maxSeen = Math.max(maxSeen, id);
            if (!running || id < offset || inFlight.contains(id) || done.contains(id)) continue;
            inFlight.add(id);
            queues.get(Math.floorMod(userKey(u), workers)).add(u);
            fresh++;
        }
        long next = nextOffset(offset);
        done.headSet(next).clear();
        // Tugamagan update'lar bo'lsa Telegram ularni darhol qaytaradi — bo'sh aylanmaslik uchun qisqa kutish
        if (fresh == 0 && !inFlight.isEmpty() && running) sleeper.accept(IDLE_REPOLL_MS);
        return next;
    }

    private long nextOffset(long offset) {
        Long first = inFlight.isEmpty() ? null : inFlight.first();
        if (first != null) return Math.max(offset, first);
        return Math.max(offset, maxSeen + 1);
    }

    private void workLoop(int i) {
        BlockingQueue<Tg.Update> q = queues.get(i);
        while (running) {
            Tg.Update u;
            try {
                u = q.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (u == null) continue;
            boolean finished = false;
            try {
                finished = process(u);
            } finally {
                if (finished) {
                    done.add(u.updateId());
                    inFlight.remove(u.updateId());
                }
            }
        }
    }

    /** true — update tugadi (muvaffaqiyatli yoki dead-letter); false — to'xtatish sababli tugallanmadi (qayta keladi). */
    boolean process(Tg.Update u) {
        long deadline = System.nanoTime() + retryWindow.toNanos();
        int backoffMs = 1000;
        while (true) {
            try {
                dispatcher.handle(u);
                status.processed();
                return true;
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable e) {
                boolean transientFailure = e instanceof RuntimeException re && Infra.isTransient(re);
                if (transientFailure && running && System.nanoTime() < deadline) {
                    status.error("update handling (will retry): " + e.getClass().getSimpleName());
                    log.warn("Update {} deferred — transient failure: {}; retry in {} ms",
                            u.updateId(), e.getClass().getSimpleName(), backoffMs);
                    sleeper.accept(backoffMs);
                    backoffMs = nextBackoff(backoffMs);
                    continue;
                }
                if (transientFailure && !running) return false;
                deadLetter(u, e, transientFailure);
                return true;
            }
        }
    }

    private void deadLetter(Tg.Update u, Throwable e, boolean afterRetries) {
        status.error("update handling: " + e.getClass().getSimpleName());
        if (afterRetries) {
            log.error("Update {} (user {}) dead-lettered after {} of transient failures", u.updateId(), userKey(u), retryWindow, e);
        } else {
            log.error("Update {} (user {}) handling failed — skipped", u.updateId(), userKey(u), e);
        }
    }

    /** Testlar uchun: barcha navbatlar bo'shab, tugamagan update qolmaguncha kutadi. */
    boolean awaitIdle(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (inFlight.isEmpty()) return true;
            sleep(5);
        }
        return inFlight.isEmpty();
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

    /**
     * Graceful shutdown: yangi update olinmaydi, worker'lar joriy update'ni tugatadi (ko'pi bilan 25 s; compose
     * {@code stop_grace_period: 30s}). Tugamagan update'lar Telegram'da tasdiqlanmagan — keyingi ishga tushishda qayta keladi.
     */
    @jakarta.annotation.PreDestroy
    public void stop() {
        running = false;
        long end = System.currentTimeMillis() + 25_000;
        List<Thread> all = new ArrayList<>(List.of(workerThreads));
        if (thread != null) all.add(thread);
        for (Thread t : all) {
            if (t == null || t == Thread.currentThread()) continue;
            if (t.getName().equals("telegram-poller")) t.interrupt(); // long-poll HTTP'ni kutmaymiz
            try {
                t.join(Math.max(1, end - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
