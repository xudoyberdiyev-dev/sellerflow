package uz.sellerflow.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.account.AccountRepositories.BusinessRepository;
import uz.sellerflow.account.AccountRepositories.ConsentRepository;
import uz.sellerflow.account.AccountRepositories.UserRepository;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.Errors;
import uz.sellerflow.common.SellerFlowProperties;

import java.time.Instant;
import java.util.Optional;

/**
 * Identifikatsiya, onboarding faollashtirish, profil va akkaunt holatlari (SF-PRD-01, SF-PRD-02.02, 02.10).
 * Telegram User ID ishonchli identifikator — u faqat Telegram update'dan olinadi, client body'dan emas.
 */
@Service
public class AccountService {

    public static final String FREE_PLAN = "FREE";

    public record AccountContext(UserAccount user, Business business) {
        public boolean isActive() { return user.getStatus() == AccountStatus.ACTIVE && business != null; }
    }

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final ConsentRepository consents;
    private final AuditService audit;
    private final SellerFlowProperties props;

    public AccountService(UserRepository users, BusinessRepository businesses, ConsentRepository consents,
                          AuditService audit, SellerFlowProperties props) {
        this.users = users;
        this.businesses = businesses;
        this.consents = consents;
        this.audit = audit;
        this.props = props;
    }

    public String currentTermsVersion() {
        return props.terms().version();
    }

    /** /start: mavjud foydalanuvchini qaytaradi yoki ONBOARDING holatida yangisini yaratadi. Takror chaqiruv dublikat yaratmaydi. */
    @Transactional
    public AccountContext findOrCreate(long telegramUserId) {
        Optional<UserAccount> existing = users.findByTelegramUserId(telegramUserId);
        // Unique(telegram_user_id) parallel yaratishdan himoya qiladi; bot update'lari ketma-ket qayta ishlanadi.
        UserAccount user = existing.orElseGet(() -> {
            UserAccount u = users.saveAndFlush(new UserAccount(telegramUserId));
            audit.record(Actor.USER, u.getId(), null, "USER_REGISTERED", "USER", u.getId(), "onboarding started");
            return u;
        });
        return new AccountContext(user, businesses.findByOwnerUserId(user.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Optional<AccountContext> find(long telegramUserId) {
        return users.findByTelegramUserId(telegramUserId)
                .map(u -> new AccountContext(u, businesses.findByOwnerUserId(u.getId()).orElse(null)));
    }

    /** Aniq, versiyalangan rozilik (UX-DEC onboarding consent). Takror bosish yangi yozuv yaratmaydi. */
    @Transactional
    public void recordConsent(long userId) {
        String version = currentTermsVersion();
        if (consents.findByUserIdAndTermsVersion(userId, version).isPresent()) return;
        consents.save(new Consent(userId, version));
        audit.record(Actor.USER, userId, null, "CONSENT_ACCEPTED", "CONSENT", version, "terms " + version);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentConsent(long userId) {
        return consents.findByUserIdAndTermsVersion(userId, currentTermsVersion()).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Consent> latestConsent(long userId) {
        return consents.findAllByUser(userId).stream().findFirst();
    }

    /**
     * Explicit faollashtirish: biznes + Free entitlement + ACTIVE — bitta tranzaksiyada, bir martalik.
     * Takror «Faollashtirish» yoki /start ikkinchi biznes/Free yaratmaydi (UX-TARIFF-005).
     */
    @Transactional
    public AccountContext activate(long userId, SalesChannel channel, String businessName, String displayName) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new Errors.NotFound("user"));
        Optional<Business> existing = businesses.findByOwnerUserId(userId);
        if (existing.isPresent()) {
            return new AccountContext(user, existing.get());
        }
        if (user.getStatus() != AccountStatus.ONBOARDING) {
            throw new Errors.Forbidden("Account is not in onboarding");
        }
        if (channel == null) throw new Errors.Validation("CHANNEL_REQUIRED");
        if (!hasCurrentConsent(userId)) throw new Errors.Validation("CONSENT_REQUIRED");
        Business b = businesses.saveAndFlush(new Business(userId, channel,
                ProfileRules.normalizeBusinessName(businessName), ProfileRules.normalizeDisplayName(displayName),
                FREE_PLAN));
        user.setStatus(AccountStatus.ACTIVE);
        audit.record(Actor.USER, userId, b.getId(), "ACCOUNT_ACTIVATED", "BUSINESS", b.getId(),
                "channel=" + channel + ", plan=" + FREE_PLAN);
        return new AccountContext(user, b);
    }

    @Transactional
    public Business updateBusinessName(long businessId, String value) {
        Business b = businesses.findById(businessId).orElseThrow(() -> new Errors.NotFound("business"));
        b.setBusinessName(ProfileRules.normalizeBusinessName(value));
        audit.record(Actor.USER, b.getOwnerUserId(), businessId, "PROFILE_BUSINESS_NAME_CHANGED", "BUSINESS", businessId,
                value == null ? "cleared" : "updated");
        return b;
    }

    @Transactional
    public Business updateDisplayName(long businessId, String value) {
        Business b = businesses.findById(businessId).orElseThrow(() -> new Errors.NotFound("business"));
        b.setDisplayName(ProfileRules.normalizeDisplayName(value));
        audit.record(Actor.USER, b.getOwnerUserId(), businessId, "PROFILE_DISPLAY_NAME_CHANGED", "BUSINESS", businessId,
                value == null ? "cleared" : "updated");
        return b;
    }

    /** Primary channel o'zgarishi eski tarix snapshotlarini va faol qoralamani o'zgartirmaydi (UX-SET-020/021). */
    @Transactional
    public Business updatePrimaryChannel(long businessId, SalesChannel channel) {
        Business b = businesses.findById(businessId).orElseThrow(() -> new Errors.NotFound("business"));
        SalesChannel old = b.getPrimaryChannel();
        if (old == channel) return b;
        b.setPrimaryChannel(channel);
        audit.record(Actor.USER, b.getOwnerUserId(), businessId, "PRIMARY_CHANNEL_CHANGED", "BUSINESS", businessId,
                old + " -> " + channel);
        return b;
    }

    /** Ikki bosqichli tasdiqdan keyin chaqiriladi. Idempotent (UX-SET-033). */
    @Transactional
    public UserAccount requestDeletion(long userId) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new Errors.NotFound("user"));
        if (u.getStatus() == AccountStatus.DELETION_REQUESTED) return u;
        if (u.getStatus() != AccountStatus.ACTIVE) throw new Errors.Forbidden("Deletion not allowed in " + u.getStatus());
        u.markDeletionRequested(Instant.now());
        Long businessId = businesses.findByOwnerUserId(userId).map(Business::getId).orElse(null);
        audit.record(Actor.USER, userId, businessId, "DELETION_REQUESTED", "USER", userId, "grace 30 days");
        return u;
    }

    /**
     * M-26: onboardingni tugatmagan (biznes yo'q) foydalanuvchi ma'lumotini o'chiradi: qoralama o'chiriladi,
     * yozuv CLOSED va Telegram ID psevdonimga almashtiriladi. Rozilik yozuvlari (bo'lsa) shaxssiz dalil sifatida qoladi.
     */
    @Transactional
    public void wipeOnboarding(long userId) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new Errors.NotFound("user"));
        if (u.getStatus() != AccountStatus.ONBOARDING || businesses.findByOwnerUserId(userId).isPresent()) {
            throw new Errors.Forbidden("Wipe allowed only during onboarding");
        }
        u.detachTelegramId(pseudonymousTelegramId());
        u.setStatus(AccountStatus.CLOSED);
        users.saveAndFlush(u);
        audit.record(Actor.USER, userId, null, "ONBOARDING_DATA_DELETED", "USER", userId, "by user before activation");
    }

    /** DELETION_PURGE_DATA — o'chirish matni haqiqatga mos bo'lishi uchun (M-23). */
    public boolean purgeEnabled() {
        return props.deletion() != null && props.deletion().purgeData();
    }

    /** M-25: kutish davrida foydalanuvchi o'zi bekor qiladi. Idempotent. */
    @Transactional
    public UserAccount cancelDeletion(long userId) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new Errors.NotFound("user"));
        if (u.getStatus() == AccountStatus.ACTIVE) return u;
        if (u.getStatus() != AccountStatus.DELETION_REQUESTED) throw new Errors.Forbidden("Cancel not allowed in " + u.getStatus());
        u.cancelDeletion();
        Long businessId = businesses.findByOwnerUserId(userId).map(Business::getId).orElse(null);
        audit.record(Actor.USER, userId, businessId, "DELETION_CANCELLED", "USER", userId, "by user in bot");
        return u;
    }

    /**
     * M-24: CLOSED akkaunt egasi yangidan boshlaydi. Eski yozuv Telegram ID'dan ajratiladi (psevdonim),
     * eski ma'lumot yangi akkauntga o'tmaydi; keyingi findOrCreate yangi ONBOARDING akkaunt yaratadi.
     */
    @Transactional
    public void reopenClosed(long telegramUserId) {
        UserAccount u = users.findByTelegramUserId(telegramUserId).orElseThrow(() -> new Errors.NotFound("user"));
        if (u.getStatus() != AccountStatus.CLOSED) throw new Errors.Forbidden("Reopen not allowed in " + u.getStatus());
        u.detachTelegramId(pseudonymousTelegramId());
        users.saveAndFlush(u);
        audit.record(Actor.USER, u.getId(), null, "CLOSED_ACCOUNT_DETACHED", "USER", u.getId(),
                "user started a new account; old data not transferred");
    }

    private static final java.security.SecureRandom RNG = new java.security.SecureRandom();

    /** Manfiy tasodifiy qiymat — haqiqiy Telegram ID (musbat) bilan to'qnashmaydi. */
    public static long pseudonymousTelegramId() {
        return -1L - (RNG.nextLong() >>> 2);
    }

    // ----------------------------------------------------------- admin

    @Transactional
    public UserAccount adminSetStatus(long telegramUserId, AccountStatus target, String adminId, String reason) {
        UserAccount u = users.findByTelegramUserId(telegramUserId).orElseThrow(() -> new Errors.NotFound("user"));
        AccountStatus old = u.getStatus();
        if (target == AccountStatus.SUSPENDED && old != AccountStatus.ACTIVE) {
            throw new Errors.Validation("ONLY_ACTIVE_CAN_BE_SUSPENDED");
        }
        if (target == AccountStatus.ACTIVE && old != AccountStatus.SUSPENDED && old != AccountStatus.DELETION_REQUESTED) {
            throw new Errors.Validation("CANNOT_REACTIVATE_FROM_" + old);
        }
        if (target == AccountStatus.ACTIVE && businesses.findByOwnerUserId(u.getId()).isEmpty()) {
            throw new Errors.Validation("NO_BUSINESS");
        }
        u.setStatus(target);
        Long businessId = businesses.findByOwnerUserId(u.getId()).map(Business::getId).orElse(null);
        audit.record(Actor.ADMIN, adminId, businessId, "ADMIN_STATUS_CHANGE", "USER", u.getId(),
                old + " -> " + target + (reason == null ? "" : "; reason: " + reason));
        return u;
    }

    /** Profil maydonlari qoidalari (PRD-02.10 DRAFT validatsiyasi). */
    public static final class ProfileRules {
        private ProfileRules() {}

        public static String normalizeBusinessName(String v) {
            return normalize(v, 80, "BUSINESS_NAME_LENGTH");
        }

        public static String normalizeDisplayName(String v) {
            return normalize(v, 60, "DISPLAY_NAME_LENGTH");
        }

        private static String normalize(String v, int max, String code) {
            if (v == null) return null;
            String t = v.strip().replaceAll("\\s+", " ");
            if (t.isEmpty()) return null;
            if (t.codePointCount(0, t.length()) > max) throw new Errors.Validation(code);
            return t;
        }
    }
}
