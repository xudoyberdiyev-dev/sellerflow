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

    public final Map<Long, List<JsonNode>> salesByShop = new ConcurrentHashMap<>();
    public final Map<Long, List<JsonNode>> expensesByShop = new ConcurrentHashMap<>();
    public volatile boolean financeFails;

    @Override
    public ProductPage financeOrders(String token, List<Long> shopIds, long fromMs, long toMs, int page, int size) {
        if (financeFails) throw new UzumException(Failure.SERVER, "HTTP 500");
        return slice(salesByShop.getOrDefault(shopIds.get(0), List.of()), page, size);
    }

    @Override
    public ProductPage financeExpenses(String token, List<Long> shopIds, long fromMs, long toMs, int page, int size) {
        if (financeFails) throw new UzumException(Failure.SERVER, "HTTP 500");
        return slice(expensesByShop.getOrDefault(shopIds.get(0), List.of()), page, size);
    }

    private static ProductPage slice(List<JsonNode> all, int page, int size) {
        int from = page * size;
        return new ProductPage(from >= all.size() ? List.of() : new ArrayList<>(all.subList(from, Math.min(all.size(), from + size))), all.size());
    }

    public void setFinance(long shopId, String salesJson, String expensesJson) {
        try {
            List<JsonNode> s = new ArrayList<>(), e = new ArrayList<>();
            m.readTree(salesJson).forEach(s::add);
            m.readTree(expensesJson).forEach(e::add);
            salesByShop.put(shopId, s);
            expensesByShop.put(shopId, e);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
        salesByShop.clear();
        expensesByShop.clear();
        financeFails = false;
    }
}
