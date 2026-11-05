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
            this.summary = summary == null ? null : summary.substring(0, Math.min(summary.length(), 500));
        }

        public Long getId() { return id; }
        public String getActorType() { return actorType; }
        public String getActorId() { return actorId; }
        public Long getBusinessId() { return businessId; }
        public String getAction() { return action; }
        public String getEntityType() { return entityType; }
        public String getEntityId() { return entityId; }
        public String getSummary() { return summary; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public interface AuditRepository extends JpaRepository<AuditEvent, Long> {}

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, Object actorId, Long businessId, String action,
                       String entityType, Object entityId, String summary) {
        repo.save(new AuditEvent(actor.name(), actorId == null ? null : actorId.toString(), businessId, action,
                entityType, entityId == null ? null : entityId.toString(), summary));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
    }
}
