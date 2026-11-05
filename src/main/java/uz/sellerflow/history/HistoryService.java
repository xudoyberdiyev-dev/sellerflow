package uz.sellerflow.history;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.Errors;
import uz.sellerflow.history.HistoryEntry.Type;
import uz.sellerflow.history.HistoryEntry.Visibility;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hisoblashlar tarixi (SF-PRD-02.07). Yozuv faqat qo'lda «Saqlash» bilan yaratiladi,
 * result_id bo'yicha idempotent. Ro'yxat tenant-scoped.
 */
@Service
public class HistoryService {

    public static final int PAGE_SIZE = 10;

    public interface HistoryRepository extends JpaRepository<HistoryEntry, Long>, JpaSpecificationExecutor<HistoryEntry> {
        Optional<HistoryEntry> findByResultId(String resultId);

        Optional<HistoryEntry> findByIdAndBusinessId(Long id, Long businessId);

        @Query("select coalesce(max(h.id), 0) from HistoryEntry h where h.businessId = ?1")
        long maxId(Long businessId);
    }

    public record SaveCommand(long businessId, long userId, String resultId, Type type, Long productId,
                              String productNameSnapshot, String channelSnapshot, String inputSnapshot,
                              String expenseSnapshot, String outputSnapshot, String accuracyStatus,
                              String calculationVersion, Instant calculatedAt, Long sourceHistoryId) {}

    public record SaveResult(HistoryEntry entry, boolean created) {}

    /**
     * Filtr. from/to — half-open [from, to) (UX-HIST-040). anchorId — birinchi sahifa ochilgandagi
     * eng katta id; parallel Save sahifalashni siljitmasligi uchun (HIST-AC-013).
     */
    public record Filter(Type type, Visibility visibility, String nameQuery, Instant from, Instant to, Long anchorId) {}

    private final HistoryRepository repo;
    private final AuditService audit;

    public HistoryService(HistoryRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional
    public SaveResult save(SaveCommand c) {
        Optional<HistoryEntry> existing = repo.findByResultId(c.resultId());
        if (existing.isPresent()) {
            if (existing.get().getBusinessId() != c.businessId()) throw new Errors.NotFound("history");
            return new SaveResult(existing.get(), false);
        }
        HistoryEntry e = repo.saveAndFlush(new HistoryEntry(c.businessId(), c.userId(), c.resultId(), c.type(),
                c.productId(), c.productNameSnapshot(), c.channelSnapshot(), c.inputSnapshot(), c.expenseSnapshot(),
                c.outputSnapshot(), c.accuracyStatus(), c.calculationVersion(), c.calculatedAt(), c.sourceHistoryId()));
        audit.record(Actor.USER, c.userId(), c.businessId(), "HISTORY_SAVED", "HISTORY", e.getId(), "type=" + c.type());
        return new SaveResult(e, true);
    }

    @Transactional(readOnly = true)
    public HistoryEntry get(long businessId, long id) {
        return repo.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new Errors.NotFound("history"));
    }

    @Transactional(readOnly = true)
    public long currentAnchor(long businessId) {
        return repo.maxId(businessId);
    }

    @Transactional(readOnly = true)
    public Page<HistoryEntry> list(long businessId, Filter q, int page) {
        Specification<HistoryEntry> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("businessId"), businessId));
            ps.add(cb.equal(root.get("visibilityStatus"),
                    (q.visibility() == null ? Visibility.ACTIVE : q.visibility()).name()));
            if (q.type() != null) ps.add(cb.equal(root.get("calculationType"), q.type().name()));
            if (q.nameQuery() != null && !q.nameQuery().isBlank()) {
                String like = "%" + q.nameQuery().strip().toLowerCase()
                        .replace("%", "").replace("_", "").replace("\\", "") + "%";
                ps.add(cb.like(cb.lower(root.get("productNameSnapshot")), like));
            }
            if (q.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("savedAt"), q.from()));
            if (q.to() != null) ps.add(cb.lessThan(root.get("savedAt"), q.to()));
            if (q.anchorId() != null && q.anchorId() > 0) ps.add(cb.lessThanOrEqualTo(root.get("id"), q.anchorId()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc("savedAt"), Sort.Order.desc("id"));
        return repo.findAll(spec, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
    }

    @Transactional
    public HistoryEntry archive(long businessId, long id) {
        HistoryEntry e = get(businessId, id);
        if (e.getVisibility() == Visibility.ARCHIVED) return e;
        e.archive();
        repo.saveAndFlush(e);
        audit.record(Actor.USER, null, businessId, "HISTORY_ARCHIVED", "HISTORY", id, null);
        return e;
    }

    @Transactional
    public HistoryEntry restore(long businessId, long id) {
        HistoryEntry e = get(businessId, id);
        if (e.getVisibility() == Visibility.ACTIVE) return e;
        e.restore();
        repo.saveAndFlush(e);
        audit.record(Actor.USER, null, businessId, "HISTORY_RESTORED", "HISTORY", id, null);
        return e;
    }
}
