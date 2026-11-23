package uz.sellerflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.sellerflow.marketplace.UzumClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Testlar uchun Uzum: to'g'ri kalit — {@link #VALID}; mahsulotlar sahifalari dasturlanadi. */
public class FakeUzum extends UzumClient {

    public static final String VALID = "uzum_test_key_0123456789";
    private final ObjectMapper m = new ObjectMapper();
    public final Map<Long, List<JsonNode>> productsByShop = new ConcurrentHashMap<>();
    public final List<String> tokensSeen = new java.util.concurrent.CopyOnWriteArrayList<>();

    public FakeUzum() {
        super("http://127.0.0.1:1", 0, new ObjectMapper());
    }

    @Override
    public List<Shop> shops(String token) {
        tokensSeen.add(token);
        if (!VALID.equals(token)) throw new UzumException(Failure.INVALID_TOKEN, "HTTP 401");
        return List.of(new Shop(9001, "Test do'kon"));
    }

    @Override
    public ProductPage products(String token, long shopId, int page) {
        tokensSeen.add(token);
        if (!VALID.equals(token)) throw new UzumException(Failure.INVALID_TOKEN, "HTTP 401");
        List<JsonNode> all = productsByShop.getOrDefault(shopId, List.of());
        int from = page * PAGE_SIZE;
        List<JsonNode> slice = from >= all.size() ? List.of() : new ArrayList<>(all.subList(from, Math.min(all.size(), from + PAGE_SIZE)));
        return new ProductPage(slice, all.size());
    }

    public void setProducts(long shopId, String json) {
        try {
            List<JsonNode> l = new ArrayList<>();
            m.readTree(json).forEach(l::add);
            productsByShop.put(shopId, l);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void reset() {
        productsByShop.clear();
        tokensSeen.clear();
    }
}
