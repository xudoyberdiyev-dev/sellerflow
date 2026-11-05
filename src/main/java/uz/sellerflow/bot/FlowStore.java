package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.common.SellerFlowProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Telegram jarayon holati serverda saqlanadi (bot restartidan keyin tiklanadi, UX-CALC-039).
 * Bitta foydalanuvchi — bitta qator — bitta faol jarayon.
 *
 * Qoralama TTL: oxirgi muvaffaqiyatli biznes kiritmasidan 24 soat (CR-DRAFT-01/02, "default" bilan qabul qilingan).
 * /help, /menu, ko'rish amallari muddatni uzaytirmaydi.
 */
@Component
public class FlowStore {

    @Entity
    @Table(name = "telegram_flows")
    public static class FlowRow {
        @Id
        @Column(name = "user_id")
        private Long userId;
        @Column(name = "flow_type")
        private String flowType;
        private String step;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb", nullable = false)
        private String data = "{}";
        private int revision;
        @Column(name = "ui_mode")
        private String uiMode;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "ui_data", columnDefinition = "jsonb", nullable = false)
        private String uiData = "{}";
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "last_input_at")
        private Instant lastInputAt;
        @Column(name = "expires_at")
        private Instant expiresAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt = Instant.now();

        protected FlowRow() {}

        FlowRow(Long userId) { this.userId = userId; }
    }

    public interface FlowRepository extends JpaRepository<FlowRow, Long> {
        @Modifying
        @Query(nativeQuery = true, value = """
                update telegram_flows set flow_type = null, step = null, data = '{}'::jsonb,
                  revision = revision + 1, last_input_at = null, expires_at = null, updated_at = now()
                where expires_at is not null and expires_at < :now""")
        int clearExpired(@org.springframework.data.repository.query.Param("now") Instant now);

        @Modifying
        @Query(nativeQuery = true, value = "delete from processed_updates where processed_at < now() - interval '7 days'")
        int purgeProcessedUpdates();
    }

    private final FlowRepository repo;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public FlowStore(FlowRepository repo, ObjectMapper mapper, SellerFlowProperties props) {
        this.repo = repo;
        this.mapper = mapper;
        this.ttl = Duration.ofHours(props.draft().ttlHours());
    }

    @Transactional
    public Session load(long userId) {
        FlowRow row = repo.findById(userId).orElseGet(() -> repo.saveAndFlush(new FlowRow(userId)));
        try {
            ObjectNode data = (ObjectNode) mapper.readTree(row.data);
            ObjectNode ui = (ObjectNode) mapper.readTree(row.uiData);
            return new Session(userId, row.flowType, row.step, data, row.revision, row.uiMode, ui,
                    row.lastInputAt, row.expiresAt, ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupted flow state for user " + userId, e);
        }
    }

    @Transactional
    public void save(Session s) {
        FlowRow row = repo.findById(s.userId()).orElseGet(() -> new FlowRow(s.userId()));
        row.flowType = s.flowType();
        row.step = s.step();
        row.data = s.data().toString();
        row.revision = s.revision();
        row.uiMode = s.uiMode();
        row.uiData = s.ui().toString();
        row.lastInputAt = s.lastInputAt();
        row.expiresAt = s.expiresAt();
        row.updatedAt = Instant.now();
        repo.save(row);
    }

    /** Muddati o'tgan qoralamalarni tozalash (saqlangan tarix va katalogga ta'sir qilmaydi, UX-HIST-039). */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public int purgeExpired() {
        return repo.clearExpired(Instant.now());
    }

    /** Takror update himoyasi uchun 7 kun yetarli; jadval cheksiz o'smasligi uchun tozalanadi. */
    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT5M")
    @Transactional
    public void purgeProcessedUpdates() {
        repo.purgeProcessedUpdates();
    }
}
