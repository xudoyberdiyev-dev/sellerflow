package uz.sellerflow.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.account.AccountRepositories.BusinessRepository;
import uz.sellerflow.account.AccountRepositories.UserRepository;
import uz.sellerflow.account.Business;
import uz.sellerflow.account.UserAccount;
import uz.sellerflow.admin.AdminAuthService.Principal;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.catalog.Product;
import uz.sellerflow.catalog.ProductService;
import uz.sellerflow.common.Errors;
import uz.sellerflow.history.HistoryEntry;
import uz.sellerflow.history.HistoryService;
import uz.sellerflow.telegram.Tg.Kb;
import uz.sellerflow.telegram.TelegramGateway;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tijoriy ma'lumotga rozilik asosidagi vaqtinchalik kirish (SF-PRD-01 5.4, Master 16.5, CR-ADMIN-01).
 * Admin so'raydi -> sotuvchi botda aniq tasdiqlaydi -> faqat READ-ONLY, 30–60 daqiqa, faqat so'ragan admin uchun,
 * har ko'rish auditga yoziladi; sotuvchi yoki admin istalgan payt bekor qila oladi; akkaunt cheklansa darhol tugaydi.
 */
@Service
public class AccessGrantService {

    public enum Status { REQUESTED, APPROVED, DECLINED, REVOKED, EXPIRED }

    /** Sotuvchi javob bermasa so'rov shu muddatdan keyin eskiradi. */
    static final Duration REQUEST_TTL = Duration.ofHours(24);

    @Entity
    @Table(name = "access_grants")
    public static class Grant {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @jakarta.persistence.Version
        private long version;
        @Column(name = "business_id", nullable = false)
        private Long businessId;
        @Column(name = "admin_id", nullable = false)
        private Long adminId;
        @Column(nullable = false)
        private String purpose;
        @Column(nullable = false)
        private String scope = "PRODUCTS_AND_HISTORY";
        @Column(nullable = false)
        private int minutes;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Status status = Status.REQUESTED;
        @Column(name = "requested_at", nullable = false)
        private Instant requestedAt = Instant.now();
        @Column(name = "decided_at")
        private Instant decidedAt;
        @Column(name = "expires_at")
        private Instant expiresAt;
        @Column(name = "revoked_at")
        private Instant revokedAt;
        @Column(name = "revoked_by")
        private String revokedBy;

        protected Grant() {}

        Grant(Long businessId, Long adminId, String purpose, int minutes) {
            this.businessId = businessId;
            this.adminId = adminId;
            this.purpose = purpose;
            this.minutes = minutes;
        }

        public Long getId() { return id; }
        public Long getBusinessId() { return businessId; }
        public Long getAdminId() { return adminId; }
        public String getPurpose() { return purpose; }
        public String getScope() { return scope; }
        public int getMinutes() { return minutes; }
        public Instant getRequestedAt() { return requestedAt; }
        public Instant getDecidedAt() { return decidedAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public Instant getRevokedAt() { return revokedAt; }
        public String getRevokedBy() { return revokedBy; }

        /** Vaqt o'tgan bo'lsa EXPIRED deb hisoblanadi (DB yangilanishini kutmasdan). */
        public Status effectiveStatus(Instant now) {
            if (status == Status.APPROVED && expiresAt != null && !now.isBefore(expiresAt)) return Status.EXPIRED;
            if (status == Status.REQUESTED && now.isAfter(requestedAt.plus(REQUEST_TTL))) return Status.EXPIRED;
            return status;
        }
    }

    public interface GrantRepository extends JpaRepository<Grant, Long> {
        List<Grant> findByAdminId(Long adminId, org.springframework.data.domain.Pageable p);

        List<Grant> findByBusinessId(Long businessId, org.springframework.data.domain.Pageable p);

        List<Grant> findByAdminIdAndBusinessId(Long adminId, Long businessId, org.springframework.data.domain.Pageable p);

        @Modifying
        @Query(nativeQuery = true, value = """
                update access_grants set status = 'EXPIRED', version = version + 1
                where (status = 'APPROVED' and expires_at <= now())
                   or (status = 'REQUESTED' and requested_at < now() - interval '24 hours')""")
        int expireOld();

        @Query(nativeQuery = true, value = """
                select count(*) from access_grants
                where admin_id = ?1 and business_id = ?2 and requested_at > now() - interval '24 hours'""")
        long countRecent(Long adminId, Long businessId);
    }

    public record DataView(Grant grant, List<Product> products, List<HistoryEntry> history,
                           long productTotal, int productPages, int productSize,
                           long historyTotal, int historyPages, int historySize) {}

    private final GrantRepository grants;
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final ProductService products;
    private final HistoryService history;
    private final AuditService audit;
    private final TelegramGateway tg;

    public AccessGrantService(GrantRepository grants, UserRepository users, BusinessRepository businesses,
                              ProductService products, HistoryService history, AuditService audit, TelegramGateway tg) {
        this.grants = grants;
        this.users = users;
        this.businesses = businesses;
        this.products = products;
        this.history = history;
        this.audit = audit;
        this.tg = tg;
    }

    // ================================================================= admin side

    /** SUPPORT roli uchun: bitta sotuvchiga 24 soatda ko'pi bilan shuncha kirish so'rovi. */
    static final int SUPPORT_GRANTS_PER_DAY = 3;

    @Transactional
    public Grant request(Principal admin, long telegramUserId, String purpose, int minutes) {
        if (purpose == null || purpose.strip().length() < 10) throw new Errors.Validation("PURPOSE_REQUIRED");
        if (minutes < 30 || minutes > 60) throw new Errors.Validation("MINUTES_RANGE");
        UserAccount u = users.findByTelegramUserId(telegramUserId).orElseThrow(() -> new Errors.NotFound("seller"));
        if (u.getStatus() != AccountStatus.ACTIVE) throw new Errors.Validation("SELLER_NOT_ACTIVE");
        Business b = businesses.findByOwnerUserId(u.getId()).orElseThrow(() -> new Errors.NotFound("seller"));
        if (!admin.isSuperadmin() && grants.countRecent(admin.id(), b.getId()) >= SUPPORT_GRANTS_PER_DAY) {
            // Sotuvchini so'rovlar bilan bezovta qilmaslik / ijtimoiy muhandislikni cheklash
            throw new Errors.Validation("GRANT_LIMIT");
        }
        String p = purpose.strip();
        Grant g = grants.saveAndFlush(new Grant(b.getId(), admin.id(), uz.sellerflow.common.TextUtil.truncate(p, 300), minutes));
        audit.record(Actor.ADMIN, admin.email(), b.getId(), "ACCESS_REQUESTED", "ACCESS_GRANT", g.getId(),
                "minutes=" + minutes + "; purpose: " + g.purpose);
        sendAfterCommit(telegramUserId, String.format(
                "🔐 <b>Qo‘llab-quvvatlash so‘rovi</b>\n\nSellerFlow administratori sizning <b>mahsulotlaringiz va saqlangan hisoblashlaringizni</b> "
                        + "faqat <b>ko‘rish</b> (o‘zgartirmasdan) uchun <b>%d daqiqaga</b> ruxsat so‘ramoqda.\n\nSabab: <i>%s</i>\n\n"
                        + "Ruxsat bermasangiz, hech narsa ko‘rsatilmaydi. Ruxsatni istalgan payt bekor qilishingiz mumkin.",
                minutes, esc(g.purpose)), Kb.create()
                .row(Kb.cb("✅ Ruxsat berish", "g:" + g.getId() + ":a"), Kb.cb("❌ Rad etish", "g:" + g.getId() + ":d"))
                .build());
        return g;
    }

    /**
     * M-9: Telegram HTTP chaqiruvi DB tranzaksiyasi ichida bajarilmaydi (ulanish band turmaydi, rollback'da
     * sotuvchiga yolg'on xabar ketmaydi) — commit'dan keyin yuboriladi.
     */
    private void sendAfterCommit(long chatId, String html, uz.sellerflow.telegram.Tg.Markup markup) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            tg.sendMessage(chatId, html, markup);
                        }
                    });
        } else {
            tg.sendMessage(chatId, html, markup);
        }
    }

    @Transactional(readOnly = true)
    public List<Grant> listFor(Principal admin) {
        return listFor(admin, null);
    }

    /** D-5: ixtiyoriy sotuvchi (biznes) filtri bilan. */
    public List<Grant> listFor(Principal admin, Long businessId) {
        var page = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id"));
        if (businessId == null) {
            return admin.isSuperadmin() ? grants.findAll(page).getContent() : grants.findByAdminId(admin.id(), page);
        }
        return admin.isSuperadmin() ? grants.findByBusinessId(businessId, page)
                : grants.findByAdminIdAndBusinessId(admin.id(), businessId, page);
    }

    /** Faqat so'ragan admin, faqat tasdiqlangan va muddati tugamagan ruxsat bilan, faqat faol akkaunt uchun. */
    @Transactional(noRollbackFor = Errors.Forbidden.class)
    public DataView view(Principal admin, long grantId) {
        return view(admin, grantId, 0, 0);
    }

    /** D-5: sahifalangan READ-ONLY ko'rinish (har chaqiruv auditga yoziladi). */
    @Transactional(noRollbackFor = Errors.Forbidden.class)
    public DataView view(Principal admin, long grantId, int productPage, int historyPage) {
        Grant g = grants.findById(grantId).orElseThrow(() -> new Errors.NotFound("grant"));
        if (!g.adminId.equals(admin.id())) throw new Errors.Forbidden("NOT_YOUR_GRANT");
        if (g.effectiveStatus(Instant.now()) != Status.APPROVED) throw new Errors.Forbidden("GRANT_NOT_ACTIVE");
        Business b = businesses.findById(g.businessId).orElseThrow(() -> new Errors.NotFound("seller"));
        UserAccount u = users.findById(b.getOwnerUserId()).orElseThrow();
        if (u.getStatus() != AccountStatus.ACTIVE) {
            g.status = Status.REVOKED;
            g.revokedAt = Instant.now();
            g.revokedBy = "SYSTEM";
            throw new Errors.Forbidden("GRANT_NOT_ACTIVE");
        }
        var pp = products.list(b.getId(), Product.Status.ACTIVE, productPage);
        var hp = history.list(b.getId(), new HistoryService.Filter(null, null, null, null, null, null), historyPage);
        audit.record(Actor.ADMIN, admin.email(), b.getId(), "ACCESS_DATA_VIEWED", "ACCESS_GRANT", g.getId(),
                "read-only p" + productPage + "/h" + historyPage);
        return new DataView(g, new java.util.ArrayList<>(pp.getContent()), hp.getContent(),
                pp.getTotalElements(), pp.getTotalPages(), pp.getSize(), hp.getTotalElements(), hp.getTotalPages(), hp.getSize());
    }

    /** 13-noyabr LOW: viewer uchun yengil holat tekshiruvi (auditlanmaydi, tijoriy ma'lumotsiz). */
    @Transactional(readOnly = true)
    public Grant ownGrant(Principal admin, long grantId) {
        Grant g = grants.findById(grantId).orElseThrow(() -> new Errors.NotFound("grant"));
        if (!g.adminId.equals(admin.id()) && !admin.isSuperadmin()) throw new Errors.Forbidden("NOT_YOUR_GRANT");
        return g;
    }

    @Transactional
    public Grant revokeByAdmin(Principal admin, long grantId) {
        Grant g = grants.findById(grantId).orElseThrow(() -> new Errors.NotFound("grant"));
        if (!g.adminId.equals(admin.id()) && !admin.isSuperadmin()) throw new Errors.Forbidden("NOT_YOUR_GRANT");
        Status st = g.effectiveStatus(Instant.now());
        if (st == Status.APPROVED || st == Status.REQUESTED) {
            g.status = Status.REVOKED;
            g.revokedAt = Instant.now();
            g.revokedBy = "ADMIN";
            audit.record(Actor.ADMIN, admin.email(), g.businessId, "ACCESS_REVOKED", "ACCESS_GRANT", g.getId(), "by admin");
        }
        return g;
    }

    // ================================================================= seller side (bot)

    public enum Decision { APPROVE, DECLINE, REVOKE }

    /** Sotuvchining bot orqali qarori. Faqat o'z biznesiga tegishli so'rov bo'yicha. */
    @Transactional
    public Map.Entry<Grant, Boolean> decide(long businessId, long grantId, Decision d) {
        Grant g = grants.findById(grantId).orElseThrow(() -> new Errors.NotFound("grant"));
        if (!g.businessId.equals(businessId)) throw new Errors.NotFound("grant");
        Instant now = Instant.now();
        Status st = g.effectiveStatus(now);
        boolean changed = false;
        switch (d) {
            case APPROVE -> {
                if (st == Status.REQUESTED) {
                    g.status = Status.APPROVED;
                    g.decidedAt = now;
                    g.expiresAt = now.plus(Duration.ofMinutes(g.minutes));
                    changed = true;
                    audit.record(Actor.USER, null, businessId, "ACCESS_APPROVED", "ACCESS_GRANT", g.getId(),
                            "minutes=" + g.minutes);
                }
            }
            case DECLINE -> {
                if (st == Status.REQUESTED) {
                    g.status = Status.DECLINED;
                    g.decidedAt = now;
                    changed = true;
                    audit.record(Actor.USER, null, businessId, "ACCESS_DECLINED", "ACCESS_GRANT", g.getId(), null);
                }
            }
            case REVOKE -> {
                if (st == Status.APPROVED || st == Status.REQUESTED) {
                    g.status = Status.REVOKED;
                    g.revokedAt = now;
                    g.revokedBy = "SELLER";
                    changed = true;
                    audit.record(Actor.USER, null, businessId, "ACCESS_REVOKED", "ACCESS_GRANT", g.getId(), "by seller");
                }
            }
        }
        return Map.entry(g, changed);
    }

    public Status statusOf(Grant g) { return g.effectiveStatus(Instant.now()); }

    /** Admin ro'yxati uchun: ID'lar bo'yicha (filtr va huquq tekshiruvi chaqiruvchida). */
    @Transactional(readOnly = true)
    public List<Grant> findAllById(List<Long> ids) {
        return grants.findAllById(ids);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void expire() {
        grants.expireOld();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
