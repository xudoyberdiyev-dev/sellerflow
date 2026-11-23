package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.catalog.ProductImportService;
import uz.sellerflow.catalog.ProductImportService.Row;
import uz.sellerflow.common.SecretBox;
import uz.sellerflow.marketplace.UzumClient.Failure;
import uz.sellerflow.marketplace.UzumClient.Shop;
import uz.sellerflow.marketplace.UzumClient.UzumException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V2: sotuvchining Uzum ulanishi — kalitni shifrlab saqlash, tekshirish, mahsulotlarni sinxronlash.
 * Kalit faqat {@link SecretBox} bilan (AAD = biznes ID), ochiq ko'rinishda faqat so'rov paytida xotirada.
 */
@Service
public class UzumService {

    private static final Logger log = LoggerFactory.getLogger(UzumService.class);
    public static final String MP = "UZUM";
    public static final Duration MANUAL_SYNC_COOLDOWN = Duration.ofMinutes(10);
    static final int MAX_PAGES_PER_SHOP = 60;
    private static final Set<String> SKIP_STATUSES = Set.of("ARCHIVED", "DELETED", "PERM_BANNED");

    public record Connection(String tokenHint, List<Long> shopIds, String status, Instant lastSyncAt, Integer lastSyncCount,
                             String lastError, Instant syncStartedAt) {}

    public record SyncResult(int skus, int created, int updated, int skipped, int shops) {}

    public enum ConnectError { DISABLED, INVALID_TOKEN, NO_SHOPS, WAF, QUOTA, UNAVAILABLE }

    public static final class ConnectException extends RuntimeException {
        private final ConnectError error;
        public ConnectException(ConnectError e) {
            super(e.name());
            this.error = e;
        }
        public ConnectError error() { return error; }
    }

    private final JdbcTemplate jdbc;
    private final SecretBox box;
    private final UzumClient client;
    private final ProductImportService importer;
    private final AuditService audit;
    private final boolean syncEnabled;

    public UzumService(JdbcTemplate jdbc, SecretBox box, UzumClient client, ProductImportService importer, AuditService audit,
                       @Value("${sellerflow.uzum.sync-enabled:true}") boolean syncEnabled) {
        this.jdbc = jdbc;
        this.box = box;
        this.client = client;
        this.importer = importer;
        this.audit = audit;
        this.syncEnabled = syncEnabled;
    }

    public boolean enabled() { return box.enabled(); }

    private static String aad(long businessId) { return "business:" + businessId + ":" + MP; }

    /** Kalitni tekshiradi (do'konlar ro'yxati) va shifrlab saqlaydi. Kalit noto'g'ri bo'lsa — hech narsa saqlanmaydi. */
    public List<Shop> connect(long businessId, String rawToken) {
        if (!box.enabled()) throw new ConnectException(ConnectError.DISABLED);
        String token = rawToken == null ? "" : rawToken.strip();
        if (token.startsWith("Bearer ")) token = token.substring(7).strip();
        if (token.length() < 10 || token.length() > 512 || token.chars().anyMatch(Character::isWhitespace)) {
            throw new ConnectException(ConnectError.INVALID_TOKEN);
        }
        List<Shop> shops;
        try {
            shops = client.shops(token);
        } catch (UzumException e) {
            throw new ConnectException(map(e.failure()));
        }
        if (shops.isEmpty()) throw new ConnectException(ConnectError.NO_SHOPS);
        String ids = shops.stream().map(s -> String.valueOf(s.id())).collect(Collectors.joining(","));
        if (ids.length() > 500) ids = ids.substring(0, ids.lastIndexOf(',', 500));
        String hint = "…" + token.substring(token.length() - 4);
        save(businessId, box.encrypt(token, aad(businessId)), hint, ids, shops.size());
        return shops;
    }

    void save(long businessId, byte[] cipher, String hint, String ids, int shopCount) {
        jdbc.update("""
                insert into marketplace_connections(business_id, marketplace, token_ciphertext, token_hint, shop_ids, status)
                values (?, ?, ?, ?, ?, 'ACTIVE')
                on conflict (business_id, marketplace) do update set token_ciphertext = excluded.token_ciphertext,
                  token_hint = excluded.token_hint, shop_ids = excluded.shop_ids, status = 'ACTIVE', last_error = null,
                  sync_started_at = null, updated_at = now()""", businessId, MP, cipher, hint, ids);
        audit.recordStandalone(Actor.USER, null, businessId, "MARKETPLACE_CONNECTED", "BUSINESS", businessId, MP + ", shops=" + shopCount);
    }

    @Transactional
    public boolean disconnect(long businessId) {
        boolean removed = jdbc.update("delete from marketplace_connections where business_id = ? and marketplace = ?", businessId, MP) > 0;
        if (removed) audit.record(Actor.USER, null, businessId, "MARKETPLACE_DISCONNECTED", "BUSINESS", businessId, MP);
        return removed;
    }

    public Optional<Connection> connection(long businessId) {
        return jdbc.query("""
                select token_hint, shop_ids, status, last_sync_at, last_sync_count, last_error, sync_started_at
                from marketplace_connections where business_id = ? and marketplace = ?""", (rs, i) -> new Connection(
                rs.getString(1), parseIds(rs.getString(2)), rs.getString(3),
                ts(rs.getTimestamp(4)), (Integer) rs.getObject(5), rs.getString(6), ts(rs.getTimestamp(7))), businessId, MP)
                .stream().findFirst();
    }

    /** Qo'lda sinxron ruxsat etilgan vaqt (bo'sh — hozir mumkin). */
    public Optional<Instant> manualSyncAllowedAt(long businessId) {
        return connection(businessId).map(Connection::lastSyncAt).map(t -> t.plus(MANUAL_SYNC_COOLDOWN))
                .filter(t -> t.isAfter(Instant.now()));
    }

    /**
     * Mahsulotlarni sinxronlash. Bir vaqtda bitta biznes uchun bitta sinxron (30 daqiqadan eski «osilgan» belgi bekor).
     * @return bo'sh — sinxron allaqachon ketyapti
     */
    public Optional<SyncResult> sync(long businessId) {
        int claimed = jdbc.update("""
                update marketplace_connections set sync_started_at = now()
                where business_id = ? and marketplace = ? and (sync_started_at is null or sync_started_at < now() - interval '30 minutes')""",
                businessId, MP);
        if (claimed == 0) return Optional.empty();
        try {
            byte[] cipher = jdbc.queryForObject("select token_ciphertext from marketplace_connections where business_id = ? and marketplace = ?",
                    byte[].class, businessId, MP);
            String token = box.decrypt(cipher, aad(businessId));
            List<Long> shopIds = connection(businessId).map(Connection::shopIds).orElse(List.of());
            List<Row> rows = new ArrayList<>();
            for (long shopId : shopIds) {
                int page = 0;
                long seen = 0;
                while (page < MAX_PAGES_PER_SHOP) {
                    var pg = client.products(token, shopId, page);
                    if (pg.products().isEmpty()) break;
                    for (JsonNode p : pg.products()) {
                        seen++;
                        rows.addAll(toRows(shopId, p));
                    }
                    if (pg.total() >= 0 && seen >= pg.total()) break;
                    if (pg.products().size() < UzumClient.PAGE_SIZE) break;
                    page++;
                }
            }
            var r = importer.upsert(businessId, ProductImportService.Source.UZUM, rows);
            jdbc.update("""
                    update marketplace_connections set sync_started_at = null, last_sync_at = now(), last_sync_count = ?,
                      last_error = null, status = 'ACTIVE', updated_at = now() where business_id = ? and marketplace = ?""",
                    rows.size(), businessId, MP);
            return Optional.of(new SyncResult(rows.size(), r.created(), r.updated(), r.skipped(), shopIds.size()));
        } catch (UzumException e) {
            String status = e.failure() == Failure.INVALID_TOKEN ? "INVALID" : e.failure() == Failure.QUOTA_EXHAUSTED ? "QUOTA" : "ACTIVE";
            jdbc.update("""
                    update marketplace_connections set sync_started_at = null, last_error = ?, status = ?, updated_at = now()
                    where business_id = ? and marketplace = ?""", e.failure().name(), status, businessId, MP);
            throw e;
        } catch (RuntimeException e) {
            jdbc.update("update marketplace_connections set sync_started_at = null, last_error = ? where business_id = ? and marketplace = ?",
                    "INTERNAL", businessId, MP);
            throw e;
        }
    }

    /** Bitta Uzum mahsulot kartochkasi → har SKU uchun bitta qator. */
    static List<Row> toRows(long shopId, JsonNode p) {
        List<Row> out = new ArrayList<>();
        String status = p.path("status").path("value").asText(p.path("status").asText(""));
        if (SKIP_STATUSES.contains(status)) return out;
        long productId = p.path("productId").asLong(0);
        String title = p.path("title").asText(null);
        JsonNode skus = p.path("skuList");
        BigDecimal productCommission = dec(p.path("commissionDto").path("maxCommission"));
        if (productCommission == null) productCommission = dec(p.path("commission"));
        int skuCount = skus.size();
        for (JsonNode s : skus) {
            if (s.path("archived").asBoolean(false)) continue;
            long skuId = s.path("skuId").asLong(0);
            if (skuId == 0) continue;
            String name = title;
            String variant = firstText(s, "skuTitle", "characteristics");
            if (skuCount > 1 && variant != null && (name == null || !name.contains(variant))) name = (name == null ? "" : name + " — ") + variant;
            if (name == null) name = firstText(s, "skuFullTitle");
            BigDecimal purchase = dec(s.path("purchasePrice"));
            if (purchase != null && purchase.signum() <= 0) purchase = null;
            BigDecimal commission = dec(s.path("commission"));
            if (commission == null) commission = productCommission;
            Integer stock = null;
            if (s.has("quantityActive") || s.has("quantityFbs")) stock = s.path("quantityActive").asInt(0) + s.path("quantityFbs").asInt(0);
            out.add(new Row(skuId, productId == 0 ? null : productId, shopId, name,
                    firstText(s, "sellerItemCode", "article"), firstText(s, "barcode"),
                    purchase, dec(s.path("price")), commission, stock,
                    s.has("quantitySold") ? s.path("quantitySold").asInt() : null,
                    s.has("quantityReturned") ? s.path("quantityReturned").asInt() : null));
        }
        return out;
    }

    /** Kunlik fon sinxroni (04:40 Toshkent). Har biznes ketma-ket — Uzum limitiga hurmat. */
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Tashkent")
    public void nightly() {
        if (!syncEnabled || !box.enabled()) return;
        List<Long> ids = jdbc.queryForList("""
                select c.business_id from marketplace_connections c join businesses b on b.id = c.business_id
                join users u on u.id = b.owner_user_id
                where c.marketplace = ? and c.status <> 'INVALID' and u.status = 'ACTIVE'
                  and (c.last_sync_at is null or c.last_sync_at < now() - interval '20 hours') order by c.business_id""",
                Long.class, MP);
        for (long id : ids) {
            try {
                sync(id);
            } catch (UzumException e) {
                log.warn("Uzum nightly sync business={} failed: {}", id, e.failure());
                if (e.failure() == Failure.WAF_BLOCKED) break; // server IP bloklangan — qolganlarini ham urinmaymiz
            } catch (RuntimeException e) {
                log.warn("Uzum nightly sync business={} failed: {}", id, e.getClass().getSimpleName());
            }
        }
    }

    private static ConnectError map(Failure f) {
        return switch (f) {
            case INVALID_TOKEN, BAD_REQUEST -> ConnectError.INVALID_TOKEN;
            case WAF_BLOCKED -> ConnectError.WAF;
            case QUOTA_EXHAUSTED -> ConnectError.QUOTA;
            default -> ConnectError.UNAVAILABLE;
        };
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText(null);
            if (v != null && !v.isBlank() && !"null".equals(v)) return v.strip();
        }
        return null;
    }

    private static BigDecimal dec(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> parseIds(String s) {
        List<Long> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) {
            try {
                if (!p.isBlank()) out.add(Long.parseLong(p.strip()));
            } catch (NumberFormatException ignored) {
                // buzilgan qiymat
            }
        }
        return out;
    }

    private static Instant ts(java.sql.Timestamp t) { return t == null ? null : t.toInstant(); }
}
