package uz.sellerflow.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.Errors;
import uz.sellerflow.common.SellerFlowProperties;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * Support kontakti Superadmin konfiguratsiyasidan (UX-DEC-33). Sozlanmagan bo'lsa — botda ko'rsatilmaydi,
 * soxta havola yo'q (UX-HELP-024).
 */
@Service
public class SupportService {

    @Entity
    @Table(name = "support_settings")
    public static class SupportSettings {
        @Id
        private Short id = 1;
        private boolean enabled;
        @Column(name = "contact_label")
        private String contactLabel;
        @Column(name = "contact_url")
        private String contactUrl;
        @Column(name = "updated_at")
        private Instant updatedAt = Instant.now();

        protected SupportSettings() {}

        public boolean isEnabled() { return enabled; }
        public String getContactLabel() { return contactLabel; }
        public String getContactUrl() { return contactUrl; }
    }

    public interface SupportRepository extends JpaRepository<SupportSettings, Short> {}

    public record SupportContact(String label, String url) {}

    private final SupportRepository repo;
    private final AuditService audit;
    private final SellerFlowProperties props;

    public SupportService(SupportRepository repo, AuditService audit, SellerFlowProperties props) {
        this.repo = repo;
        this.audit = audit;
        this.props = props;
    }

    /** Muhit o'zgaruvchisida boshlang'ich kontakt berilgan va DB hali sozlanmagan bo'lsa, uni yozadi. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedFromEnvironment() {
        String url = props.support() == null ? null : props.support().defaultUrl();
        if (url == null || url.isBlank()) return;
        SupportSettings s = repo.findById((short) 1).orElseThrow();
        if (s.enabled || s.contactUrl != null) return;
        String label = props.support().defaultLabel();
        apply(s, true, label == null || label.isBlank() ? "Qo‘llab-quvvatlash" : label, url);
        audit.record(Actor.SYSTEM, "env", null, "SUPPORT_SEEDED", "SUPPORT", 1, null);
    }

    @Transactional(readOnly = true)
    public Optional<SupportContact> contact() {
        return repo.findById((short) 1)
                .filter(s -> s.enabled && s.contactUrl != null)
                .map(s -> new SupportContact(s.contactLabel == null ? "Qo‘llab-quvvatlash" : s.contactLabel, s.contactUrl));
    }

    @Transactional(readOnly = true)
    public SupportSettings settings() {
        return repo.findById((short) 1).orElseThrow();
    }

    @Transactional
    public SupportSettings update(boolean enabled, String label, String url, String adminId) {
        SupportSettings s = repo.findById((short) 1).orElseThrow();
        apply(s, enabled, label, url);
        audit.record(Actor.ADMIN, adminId, null, "SUPPORT_UPDATED", "SUPPORT", 1, "enabled=" + enabled);
        return s;
    }

    private void apply(SupportSettings s, boolean enabled, String label, String url) {
        String normalized = normalizeUrl(url);
        if (enabled && normalized == null) throw new Errors.Validation("SUPPORT_URL_REQUIRED");
        s.enabled = enabled;
        s.contactLabel = label == null || label.isBlank() ? null : label.strip();
        s.contactUrl = normalized;
        s.updatedAt = Instant.now();
        repo.save(s);
    }

    /** "@username" -> https://t.me/username; faqat https havolalar qabul qilinadi. */
    static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.strip();
        if (u.matches("^@[A-Za-z0-9_]{4,32}$")) return "https://t.me/" + u.substring(1);
        try {
            URI uri = URI.create(u);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new Errors.Validation("SUPPORT_URL_INVALID");
            }
            return u;
        } catch (IllegalArgumentException e) {
            throw new Errors.Validation("SUPPORT_URL_INVALID");
        }
    }
}
