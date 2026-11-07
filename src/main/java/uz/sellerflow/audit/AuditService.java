package uz.sellerflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Muhim amallar auditi. Summary ichiga tannarx/foyda kabi tijoriy qiymatlar, telefon yoki
 * tokenlar YOZILMAYDI (UX-PROD-045, Master 20.4 "Sensitive log").
 */
@Service
public class AuditService {

    public enum Actor { USER, ADMIN, SYSTEM }

    @Entity
    @Table(name = "audit_events")
    public static class AuditEvent {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "actor_type", nullable = false)
        private String actorType;
        @Column(name = "actor_id")
        private String actorId;
        @Column(name = "business_id")
        private Long businessId;
        @Column(nullable = false)
        private String action;
        @Column(name = "entity_type")
        private String entityType;
        @Column(name = "entity_id")
        private String entityId;
        private String summary;
        /** M-6: web so'rov manba IP'si (RemoteIpValve'dan keyin); bot/tizim hodisalari uchun null. */
        @Column(name = "source_ip")
        private String sourceIp;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        protected AuditEvent() {}

        AuditEvent(String actorType, String actorId, Long businessId, String action,
                   String entityType, String entityId, String summary) {
            this.actorType = actorType;
            this.actorId = actorId;
            this.businessId = businessId;
            this.action = action;
            this.entityType = entityType;
            this.entityId = entityId;
            this.summary = uz.sellerflow.common.TextUtil.truncate(summary, 500);
            this.sourceIp = currentRequestIp();
        }

        public Long getId() { return id; }
        public String getActorType() { return actorType; }
        public String getActorId() { return actorId; }
        public Long getBusinessId() { return businessId; }
        public String getAction() { return action; }
        public String getEntityType() { return entityType; }
        public String getEntityId() { return entityId; }
        public String getSummary() { return summary; }
        public String getSourceIp() { return sourceIp; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /** Joriy HTTP so'rov bo'lsa — mijoz IP'si (forward-headers-strategy=native hisobga olingan). */
    static String currentRequestIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            String ip = sra.getRequest().getRemoteAddr();
            return ip == null ? null : ip.substring(0, Math.min(ip.length(), 45));
        }
        return null;
    }

    public interface AuditRepository extends JpaRepository<AuditEvent, Long> {}

    private final AuditRepository repo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AuditService(AuditRepository repo, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, Object actorId, Long businessId, String action,
                       String entityType, Object entityId, String summary) {
        String aid = actorId == null ? null : actorId.toString();
        if (aid == null && actor == Actor.USER && businessId != null) {
            // LOW (maxfiylik): USER hodisasida kim qilgani — biznes egasining ichki user ID'si
            aid = jdbc.query("select owner_user_id from businesses where id = ?",
                    rs -> rs.next() ? String.valueOf(rs.getLong(1)) : null, businessId);
        }
        aid = uz.sellerflow.common.TextUtil.truncate(aid, 64);
        repo.save(new AuditEvent(actor.name(), aid, businessId, action,
                entityType, entityId == null ? null : entityId.toString(), summary));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
    }
}
