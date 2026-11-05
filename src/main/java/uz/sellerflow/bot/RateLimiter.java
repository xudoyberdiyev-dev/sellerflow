package uz.sellerflow.bot;

import org.springframework.stereotype.Component;
import uz.sellerflow.common.SellerFlowProperties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bitta Telegram foydalanuvchisi uchun sirpanuvchi oyna limiti (xotirada; bitta nusxa uchun yetarli). */
@Component
public class RateLimiter {

    public enum Verdict { OK, WARN, DROP }

    private final int max;
    private final long windowMs;
    private final Map<Long, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Map<Long, Long> warnedAt = new ConcurrentHashMap<>();

    public RateLimiter(SellerFlowProperties props) {
        var rl = props.rateLimit();
        this.max = rl == null || rl.maxUpdates() <= 0 ? 20 : rl.maxUpdates();
        this.windowMs = (rl == null || rl.windowSeconds() <= 0 ? 10 : rl.windowSeconds()) * 1000L;
    }

    public Verdict check(long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
            if (q.size() < max) {
                q.addLast(now);
                return Verdict.OK;
            }
        }
        Long w = warnedAt.get(userId);
        if (w == null || now - w > windowMs) {
            warnedAt.put(userId, now);
            return Verdict.WARN;
        }
        return Verdict.DROP;
    }

    /** Xotira o'smasligi uchun eski yozuvlarni tozalash. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT10M")
    public void cleanup() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMs;
            }
        });
        warnedAt.entrySet().removeIf(e -> now - e.getValue() > windowMs);
    }
}
