package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Uzum Market Seller OpenAPI klienti (faqat o'qish). Kalit {@code Authorization} sarlavhasida «Bearer»siz.
 * Limitlar: ~2 so'rov/s (har kalit uchun so'rovlar orasida tanaffus), kunlik 100 000.
 * 429: kunlik kvota tugagan bo'lsa ({@code x-ratelimit-remaining-per-day: 0}) — qayta urinilmaydi.
 * 403 HTML bilan — WAF bloki (kalit emas). Kalit hech qachon logga yozilmaydi.
 */
@Component
public class UzumClient {

    private static final Logger log = LoggerFactory.getLogger(UzumClient.class);
    public static final int PAGE_SIZE = 100;
    static final int MAX_ATTEMPTS = 4;

    public enum Failure { INVALID_TOKEN, WAF_BLOCKED, QUOTA_EXHAUSTED, RATE_LIMITED, SERVER, BAD_REQUEST, NETWORK }

    public static final class UzumException extends RuntimeException {
        private final Failure failure;
        public UzumException(Failure f, String msg) {
            super(msg);
            this.failure = f;
        }
        public Failure failure() { return failure; }
    }

    public record Shop(long id, String name) {}

    public record ProductPage(List<JsonNode> products, long total) {}

    private final RestClient client;
    private final ObjectMapper mapper;
    private final long gapMs;
    private final Map<Integer, Long> lastCall = new ConcurrentHashMap<>();
    private LongConsumer sleeper = UzumClient::sleep;

    public UzumClient(@Value("${sellerflow.uzum.api-base-url:https://api-seller.uzum.uz/api/seller-openapi}") String baseUrl,
                      @Value("${sellerflow.uzum.request-gap-ms:550}") long gapMs, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout(30_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
        this.mapper = mapper;
        this.gapMs = gapMs;
    }

    /** Testlar uchun. */
    void setSleeper(LongConsumer s) { this.sleeper = s; }

    public List<Shop> shops(String token) {
        JsonNode n = get(token, "/v1/shops", Map.of());
        JsonNode arr = n.isArray() ? n : n.path("payload").isArray() ? n.path("payload") : n.path("shops");
        List<Shop> out = new ArrayList<>();
        for (JsonNode s : arr) {
            if (s.hasNonNull("id")) out.add(new Shop(s.path("id").asLong(), s.path("name").asText(s.path("shopTitle").asText(""))));
        }
        return out;
    }

    public ProductPage products(String token, long shopId, int page) {
        JsonNode n = get(token, "/v1/product/shop/" + shopId, Map.of("page", page, "size", PAGE_SIZE));
        JsonNode body = n.has("productList") ? n : n.path("payload");
        List<JsonNode> list = new ArrayList<>();
        body.path("productList").forEach(list::add);
        return new ProductPage(list, body.path("totalProductsAmount").asLong(-1));
    }

    /** V3: sotuv qatorlari (group=false — har tovar qatori alohida). */
    public ProductPage financeOrders(String token, List<Long> shopIds, long fromMs, long toMs, int page, int size) {
        JsonNode n = get(token, "/v1/finance/orders", ordered("shopIds", join(shopIds), "group", false, "page", page, "size", size,
                "dateFrom", fromMs, "dateTo", toMs));
        JsonNode body = n.has("orderItems") ? n : n.path("payload");
        List<JsonNode> list = new ArrayList<>();
        body.path("orderItems").forEach(list::add);
        return new ProductPage(list, body.path("totalElements").asLong(-1));
    }

    /** V3: sotuvchi xarajatlari (saqlash, jarima, reklama...). */
    public ProductPage financeExpenses(String token, List<Long> shopIds, long fromMs, long toMs, int page, int size) {
        JsonNode n = get(token, "/v1/finance/expenses", ordered("shopIds", join(shopIds), "page", page, "size", size,
                "dateFrom", fromMs, "dateTo", toMs));
        JsonNode body = n.path("payload").isObject() ? n.path("payload") : n;
        List<JsonNode> list = new ArrayList<>();
        body.path("payments").forEach(list::add);
        return new ProductPage(list, body.path("totalElements").asLong(-1));
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private JsonNode get(String token, String path, Map<String, Object> query) {
        if (token == null || token.isBlank()) throw new UzumException(Failure.INVALID_TOKEN, "empty token");
        for (int attempt = 1; ; attempt++) {
            pace(token);
            Response r;
            try {
                r = client.get().uri(b -> {
                            b.path(path);
                            query.forEach(b::queryParam);
                            return b.build();
                        })
                        .header("Authorization", token.strip())
                        .header("Accept", "application/json")
                        .exchange((req, res) -> new Response(res.getStatusCode().value(),
                                res.getHeaders().getFirst("Content-Type"),
                                res.getHeaders().getFirst("x-ratelimit-remaining-per-day"),
                                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)), true);
            } catch (RestClientException e) {
                if (attempt < MAX_ATTEMPTS) {
                    sleeper.accept(800L << (attempt - 1));
                    continue;
                }
                throw new UzumException(Failure.NETWORK, e.getClass().getSimpleName());
            }
            if (r == null) throw new UzumException(Failure.NETWORK, "no response");
            int st = r.status;
            if (st >= 200 && st < 300) {
                try {
                    return r.body.isBlank() ? mapper.createObjectNode() : mapper.readTree(r.body);
                } catch (IOException e) {
                    throw new UzumException(Failure.SERVER, "invalid json");
                }
            }
            boolean html = r.contentType != null && r.contentType.toLowerCase().contains("text/html");
            if (st == 403 && html) throw new UzumException(Failure.WAF_BLOCKED, "403 html");
            if (st == 401 || st == 403 || (st == 404 && r.body.contains("Token not found"))) {
                throw new UzumException(Failure.INVALID_TOKEN, "HTTP " + st);
            }
            if (st == 429) {
                if ("0".equals(r.remainingPerDay)) throw new UzumException(Failure.QUOTA_EXHAUSTED, "daily quota");
                if (attempt < MAX_ATTEMPTS) {
                    sleeper.accept(1500L * attempt);
                    continue;
                }
                throw new UzumException(Failure.RATE_LIMITED, "429");
            }
            if (st >= 500) {
                if (attempt < MAX_ATTEMPTS) {
                    sleeper.accept(800L << (attempt - 1));
                    continue;
                }
                throw new UzumException(Failure.SERVER, "HTTP " + st);
            }
            log.warn("Uzum {} -> HTTP {}", path.replaceAll("\\d+", "{id}"), st);
            throw new UzumException(Failure.BAD_REQUEST, "HTTP " + st);
        }
    }

    private record Response(int status, String contentType, String remainingPerDay, String body) {}

    /** Bitta kalit bo'yicha so'rovlar orasida kamida gapMs (sekundlik limitga urilmaslik uchun). */
    private void pace(String token) {
        int k = token.hashCode();
        synchronized (this) {
            long now = System.currentTimeMillis();
            Long last = lastCall.get(k);
            long wait = last == null ? 0 : last + gapMs - now;
            if (wait > 0) sleeper.accept(wait);
            lastCall.put(k, System.currentTimeMillis());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
