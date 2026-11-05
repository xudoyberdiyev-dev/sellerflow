package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Bitta foydalanuvchining jarayon holati (FlowStore orqali saqlanadi). */
public final class Session {

    /** Faol interaktiv jarayon turlari. */
    public static final String PROFIT = "PROFIT";
    public static final String PRICE = "PRICE";
    public static final String ONBOARDING = "ONBOARDING";
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_EDIT = "PRODUCT_EDIT";
    public static final String SETTINGS_EDIT = "SETTINGS_EDIT";

    private final long userId;
    private String flowType;
    private String step;
    private ObjectNode data;
    private int revision;
    private String uiMode;
    private ObjectNode ui;
    private Instant lastInputAt;
    private Instant expiresAt;
    private final Duration ttl;

    Session(long userId, String flowType, String step, ObjectNode data, int revision, String uiMode, ObjectNode ui,
            Instant lastInputAt, Instant expiresAt, Duration ttl) {
        this.userId = userId;
        this.flowType = flowType;
        this.step = step;
        this.data = data;
        this.revision = revision;
        this.uiMode = uiMode;
        this.ui = ui;
        this.lastInputAt = lastInputAt;
        this.expiresAt = expiresAt;
        this.ttl = ttl;
    }

    public long userId() { return userId; }
    public String flowType() { return flowType; }
    public String step() { return step; }
    public ObjectNode data() { return data; }
    public int revision() { return revision; }
    public String uiMode() { return uiMode; }
    public ObjectNode ui() { return ui; }
    public Instant lastInputAt() { return lastInputAt; }
    public Instant expiresAt() { return expiresAt; }

    public boolean hasFlow() { return flowType != null; }

    public boolean isFlow(String type) { return type.equals(flowType); }

    /** Yangi jarayon: revision oshadi — eski xabarlardagi tugmalar eskiradi (stale callback). */
    public void start(String type, String firstStep) {
        this.flowType = type;
        this.step = firstStep;
        this.data = JsonNodeFactory.instance.objectNode();
        this.revision++;
        this.lastInputAt = null;
        this.expiresAt = null;
        this.uiMode = null;
    }

    public void clear() {
        this.flowType = null;
        this.step = null;
        this.data = JsonNodeFactory.instance.objectNode();
        this.revision++;
        this.lastInputAt = null;
        this.expiresAt = null;
    }

    public void step(String s) { this.step = s; }

    public void uiMode(String mode) { this.uiMode = mode; }

    /** Muvaffaqiyatli biznes kiritmasi: TTL shu paytdan 24 soat; saqlanmagan o'zgarish bor. */
    public void markInput() {
        this.lastInputAt = Instant.now();
        this.expiresAt = lastInputAt.plus(ttl);
        data.put("_dirty", true);
    }

    public void markSaved() { data.put("_dirty", false); }

    /** Chiqishda real ma'lumot yo'qolish xavfi bormi (UX-DEC-17). */
    public boolean hasUnsavedData() {
        return hasFlow() && !isFlow(ONBOARDING) && data.path("_dirty").asBoolean(false);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isPaused() { return data.path("_paused").asBoolean(false); }

    public void pause() { if (hasFlow()) data.put("_paused", true); }

    public void resume() { data.remove("_paused"); }

    // ---------------------------------------------------------------- helpers

    public String str(String key) {
        JsonNode n = data.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    public BigDecimal dec(String key) {
        String s = str(key);
        return s == null ? null : new BigDecimal(s);
    }

    public Long lng(String key) {
        JsonNode n = data.get(key);
        return n == null || n.isNull() ? null : n.asLong();
    }

    public void put(String key, String v) {
        if (v == null) data.putNull(key); else data.put(key, v);
    }

    public void put(String key, BigDecimal v) {
        if (v == null) data.putNull(key); else data.put(key, v.toPlainString());
    }

    public void put(String key, Long v) {
        if (v == null) data.putNull(key); else data.put(key, v);
    }

    public ObjectNode obj(String key) {
        JsonNode n = data.get(key);
        if (n instanceof ObjectNode o) return o;
        return data.putObject(key);
    }
}
