package uz.sellerflow.admin;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M-2: manba IP bo'yicha muvaffaqiyatsiz admin login urinishlari limiti (xotirada; bitta nusxa uchun yetarli).
 * IP limiti ({@value #MAX_FAILURES_PER_IP}) akkaunt lockout chegarasidan ({@link AdminAuthService#MAX_FAILED})
 * past — bitta IP'dan turib adminni bloklab qo'yish (lockout DoS) mumkin emas.
 * Reverse proxy ortida haqiqiy IP {@code server.forward-headers-strategy=native} (RemoteIpValve) orqali olinadi.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES_PER_IP = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** Band qilingan urinish: muvaffaqiyatli bo'lsa {@link #release} bilan qaytariladi. */
    public record Ticket(String key, Long at) {}

    /**
     * N-6: atomik «tekshir va band qil». Limit to'lgan bo'lsa — null (parol tekshirilmaydi).
     * Aks holda urinish darhol hisobga olinadi — bir vaqtdagi parallel so'rovlar ham limitdan oshmaydi.
     */
    public Ticket tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        String k = key(ip);
        Deque<Long> q = failures.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            if (q.size() >= MAX_FAILURES_PER_IP) return null;
            Long at = now; // alohida obyekt — release aynan shu yozuvni olib tashlaydi
            q.addLast(at);
            return new Ticket(k, at);
        }
    }

    public void release(Ticket t) {
        Deque<Long> q = failures.get(t.key());
        if (q == null) return;
        synchronized (q) {
            q.removeIf(x -> x == t.at());
        }
    }

    /** IP limitdan oshgan bo'lsa — true (so'rov parol tekshirilmasdan rad etiladi). */
    public boolean isBlocked(String ip) {
        Deque<Long> q = failures.get(key(ip));
        if (q == null) return false;
        long now = System.currentTimeMillis();
        synchronized (q) {
            prune(q, now);
            return q.size() >= MAX_FAILURES_PER_IP;
        }
    }

    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> q = failures.computeIfAbsent(key(ip), k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    /** Testlar va qo'lda tiklash uchun. */
    public void reset() {
        failures.clear();
    }

    @Scheduled(fixedDelayString = "PT10M")
    public void cleanup() {
        long now = System.currentTimeMillis();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > WINDOW.toMillis()) q.pollFirst();
    }

    private static String key(String ip) {
        return ip == null ? "?" : ip;
    }
}
