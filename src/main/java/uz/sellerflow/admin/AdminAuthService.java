package uz.sellerflow.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.Errors;
import uz.sellerflow.common.SellerFlowProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Admin identifikatsiyasi (sotuvchi Telegram ID'sidan alohida, Master 16.4).
 * Parol (BCrypt) + majburiy TOTP 2FA, rollar, sessiya tokenlari (DB'da faqat SHA-256 xeshi),
 * 10 ta xato urinishdan keyin 15 daqiqalik blok (IP limiti — LoginRateLimiter).
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    public enum Role { SUPERADMIN, SUPPORT }
    public enum Status { ACTIVE, DISABLED }

    static final Duration SESSION_TTL = Duration.ofHours(8);
    static final Duration SETUP_TTL = Duration.ofMinutes(15);
    /** Akkaunt lockout chegarasi; IP limiti (LoginRateLimiter, 5) undan past — bitta IP adminni bloklay olmaydi (M-2). */
    static final int MAX_FAILED = 10;
    static final Duration LOCK = Duration.ofMinutes(15);
    static final int MIN_PASSWORD = 12;

    @Entity
    @Table(name = "admins")
    public static class Admin {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(nullable = false)
        private String email;
        @Column(name = "password_hash", nullable = false)
        private String passwordHash;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Role role;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Status status = Status.ACTIVE;
        @Column(name = "totp_secret")
        private String totpSecret;
        /** M-1: oxirgi muvaffaqiyatli ishlatilgan TOTP qadami (shu yoki eskiroq qadam qayta qabul qilinmaydi). */
        @Column(name = "totp_last_step")
        private Long totpLastStep;
        @Column(name = "totp_enabled", nullable = false)
        private boolean totpEnabled;
        @Column(name = "must_change_password", nullable = false)
        private boolean mustChangePassword;
        @Column(name = "failed_attempts", nullable = false)
        private int failedAttempts;
        @Column(name = "locked_until")
        private Instant lockedUntil;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "last_login_at")
        private Instant lastLoginAt;

        protected Admin() {}

        Admin(String email, String passwordHash, Role role, boolean mustChangePassword) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.role = role;
            this.mustChangePassword = mustChangePassword;
        }

        public Long getId() { return id; }
        public String getEmail() { return email; }
        public Role getRole() { return role; }
        public Status getStatus() { return status; }
        public boolean isTotpEnabled() { return totpEnabled; }
        public boolean isMustChangePassword() { return mustChangePassword; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastLoginAt() { return lastLoginAt; }
    }

    @Entity
    @Table(name = "admin_sessions")
    public static class AdminSession {
        @Id
        @Column(name = "token_hash")
        private String tokenHash;
        @Column(name = "admin_id", nullable = false)
        private Long adminId;
        @Column(nullable = false)
        private String kind;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;
        private boolean revoked;

        protected AdminSession() {}

        AdminSession(String tokenHash, Long adminId, String kind, Instant expiresAt) {
            this.tokenHash = tokenHash;
            this.adminId = adminId;
            this.kind = kind;
            this.expiresAt = expiresAt;
        }
    }

    public interface AdminRepository extends JpaRepository<Admin, Long> {
        Optional<Admin> findByEmailIgnoreCase(String email);

        /** Atomik: faqat yangi (kattaroq) qadam yoziladi; 0 qaytsa — kod allaqachon ishlatilgan (replay). */
        @Modifying(flushAutomatically = true)
        @Query(nativeQuery = true, value = "update admins set totp_last_step = ?2 where id = ?1"
                + " and (totp_last_step is null or totp_last_step < ?2)")
        int consumeTotpStep(Long adminId, long step);

        /**
         * M-3: atomik "failed_attempts + 1"; chegaraga yetganda bloklab, hisoblagichni nollaydi.
         * Qaytaradi: 1 — shu chaqiruv akkauntni blokladi, 0 — yo'q. Persistence context tozalanadi,
         * eskirgan entity keyinroq hisoblagichni qayta yozib yubormasligi uchun.
         */
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query(nativeQuery = true, value = """
                update admins set
                  locked_until    = case when failed_attempts + 1 >= ?2 then ?3 else locked_until end,
                  failed_attempts = case when failed_attempts + 1 >= ?2 then -1 else failed_attempts + 1 end
                where id = ?1""")
        int bumpFailures(Long adminId, int max, Instant lockUntil);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query(nativeQuery = true, value = "update admins set failed_attempts = 0 where id = ?1 and failed_attempts = -1")
        int markLocked(Long adminId);

        default int registerFailure(Long adminId, int max, Instant lockUntil) {
            bumpFailures(adminId, max, lockUntil);
            return markLocked(adminId);
        }
    }

    public interface SessionRepository extends JpaRepository<AdminSession, String> {
        @Modifying
        @Query(nativeQuery = true, value = "update admin_sessions set revoked = true where admin_id = ?1")
        int revokeAll(Long adminId);

        @Modifying
        @Query(nativeQuery = true, value = "delete from admin_sessions where expires_at < now() - interval '1 day'")
        int purgeOld();
    }

    /** Autentifikatsiyadan o'tgan admin (har so'rovda serverda qayta tekshiriladi). */
    public record Principal(long id, String email, Role role) {
        public boolean isSuperadmin() { return role == Role.SUPERADMIN; }
    }

    public record LoginResult(String status, String token, String setupToken, String totpSecret, String otpauthUri,
                              Principal admin, boolean mustChangePassword) {}

    private final AdminRepository admins;
    private final SessionRepository sessions;
    private final AuditService audit;
    private final SellerFlowProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom rng = new SecureRandom();
    /** Mavjud bo'lmagan email uchun ham BCrypt tekshiruvi — vaqt bo'yicha farqlanmasligi uchun. */
    private final String dummyHash = encoder.encode("dummy-password-for-timing");

    public AdminAuthService(AdminRepository admins, SessionRepository sessions, AuditService audit,
                            SellerFlowProperties props) {
        this.admins = admins;
        this.sessions = sessions;
        this.audit = audit;
        this.props = props;
    }

    /** ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD — faqat bitta ham admin bo'lmaganda birinchi SUPERADMIN. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (admins.count() > 0) return;
        var a = props.admin();
        if (a == null || a.bootstrapEmail() == null || a.bootstrapEmail().isBlank()
                || a.bootstrapPassword() == null || a.bootstrapPassword().isBlank()) {
            log.warn("No admins exist. Set ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD to create the first SUPERADMIN.");
            return;
        }
        if (a.bootstrapPassword().length() < MIN_PASSWORD) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD must be at least {} characters — admin not created", MIN_PASSWORD);
            return;
        }
        Admin admin = admins.save(new Admin(a.bootstrapEmail().strip(), encoder.encode(a.bootstrapPassword()),
                Role.SUPERADMIN, false));
        audit.record(Actor.SYSTEM, "bootstrap", null, "ADMIN_BOOTSTRAPPED", "ADMIN", admin.getId(), null);
        log.info("First SUPERADMIN created from environment");
    }

    // ================================================================= login

    @Transactional(noRollbackFor = Errors.Forbidden.class)
    public LoginResult login(String email, String password, String code) {
        Instant now = Instant.now();
        Optional<Admin> found = email == null ? Optional.empty() : admins.findByEmailIgnoreCase(email.strip());
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw new Errors.Forbidden("INVALID_CREDENTIALS");
        }
        Admin a = found.get();
        // M-2: bloklangan/o'chirilgan akkaunt uchun ham BCrypt bajariladi va javob parolga bog'liq emas —
        // na javob, na vaqt parol to'g'riligini oshkor qilmaydi.
        boolean passwordOk = password != null && encoder.matches(password, a.passwordHash);
        if (a.status != Status.ACTIVE) throw new Errors.Forbidden("INVALID_CREDENTIALS");
        if (a.lockedUntil != null && now.isBefore(a.lockedUntil)) throw new Errors.Forbidden("LOCKED");
        if (!passwordOk) {
            registerFailure(a, now, "password");
            throw new Errors.Forbidden("INVALID_CREDENTIALS");
        }
        if (!a.totpEnabled) {
            if (a.totpSecret == null) a.totpSecret = Totp.newSecret();
            String setup = newSession(a.id, "TOTP_SETUP", now.plus(SETUP_TTL));
            admins.save(a);
            return new LoginResult("TOTP_SETUP_REQUIRED", null, setup, a.totpSecret,
                    Totp.otpauthUri("SellerFlow Admin", a.email, a.totpSecret), null, a.mustChangePassword);
        }
        if (code == null || code.isBlank()) {
            return new LoginResult("TOTP_REQUIRED", null, null, null, null, null, false);
        }
        if (!consumeTotp(a, code, now)) {
            registerFailure(a, now, "totp");
            throw new Errors.Forbidden("INVALID_CODE");
        }
        return completeLogin(a, now);
    }

    @Transactional(noRollbackFor = Errors.Forbidden.class)
    public LoginResult confirmTotp(String setupToken, String code) {
        Instant now = Instant.now();
        AdminSession s = sessions.findById(hash(setupToken)).orElseThrow(() -> new Errors.Forbidden("INVALID_SESSION"));
        if (s.revoked || !"TOTP_SETUP".equals(s.kind) || now.isAfter(s.expiresAt)) throw new Errors.Forbidden("INVALID_SESSION");
        Admin a = admins.findById(s.adminId).orElseThrow(() -> new Errors.Forbidden("INVALID_SESSION"));
        if (!consumeTotp(a, code, now)) {
            registerFailure(a, now, "totp-setup");
            throw new Errors.Forbidden("INVALID_CODE");
        }
        a.totpEnabled = true;
        s.revoked = true;
        sessions.save(s);
        audit.record(Actor.ADMIN, a.email, null, "ADMIN_2FA_ENABLED", "ADMIN", a.id, null);
        return completeLogin(a, now);
    }

    /** Kod to'g'ri va uning qadami hali ishlatilmagan bo'lsa — qadamni band qiladi (M-1, replay himoyasi). */
    private boolean consumeTotp(Admin a, String code, Instant now) {
        var step = Totp.matchStep(a.totpSecret, code, now.getEpochSecond());
        if (step.isEmpty()) return false;
        if (a.totpLastStep != null && step.getAsLong() <= a.totpLastStep) return false;
        if (admins.consumeTotpStep(a.id, step.getAsLong()) == 0) return false;
        a.totpLastStep = step.getAsLong();
        return true;
    }

    private LoginResult completeLogin(Admin a, Instant now) {
        a.failedAttempts = 0;
        a.lockedUntil = null;
        a.lastLoginAt = now;
        admins.save(a);
        String token = newSession(a.id, "FULL", now.plus(SESSION_TTL));
        audit.record(Actor.ADMIN, a.email, null, "ADMIN_LOGIN", "ADMIN", a.id, null);
        return new LoginResult("OK", token, null, null, null, principal(a), a.mustChangePassword);
    }

    /** M-3: hisoblagich DB'da atomik oshiriladi (parallel so'rovlarda o'sish yo'qolmaydi). */
    private void registerFailure(Admin a, Instant now, String what) {
        if (admins.registerFailure(a.id, MAX_FAILED, now.plus(LOCK)) > 0) {
            audit.record(Actor.SYSTEM, "auth", null, "ADMIN_LOCKED", "ADMIN", a.id, "after failed " + what);
        }
    }

    /** Har so'rovda: token -> faol admin. Rol va holat DB'dan o'qiladi (o'chirilgan admin darhol chiqariladi). */
    @Transactional(readOnly = true)
    public Optional<Principal> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(hash(token))
                .filter(s -> !s.revoked && "FULL".equals(s.kind) && Instant.now().isBefore(s.expiresAt))
                .flatMap(s -> admins.findById(s.adminId))
                .filter(a -> a.status == Status.ACTIVE)
                .map(AdminAuthService::principal);
    }

    @Transactional
    public void logout(String token) {
        sessions.findById(hash(token)).ifPresent(s -> {
            s.revoked = true;
            sessions.save(s);
        });
    }

    @Transactional
    public void changePassword(Principal p, String current, String next) {
        Admin a = admins.findById(p.id()).orElseThrow(() -> new Errors.NotFound("admin"));
        if (!encoder.matches(current == null ? "" : current, a.passwordHash)) throw new Errors.Validation("WRONG_PASSWORD");
        validatePassword(next);
        a.passwordHash = encoder.encode(next);
        a.mustChangePassword = false;
        admins.save(a);
        audit.record(Actor.ADMIN, p.email(), null, "ADMIN_PASSWORD_CHANGED", "ADMIN", a.id, null);
    }

    // ================================================================= admin management (SUPERADMIN)

    @Transactional(readOnly = true)
    public List<Admin> list() {
        return admins.findAll().stream().sorted((x, y) -> Long.compare(x.id, y.id)).toList();
    }

    @Transactional
    public Admin create(Principal by, String email, Role role, String tempPassword) {
        requireSuper(by);
        if (email == null || !email.strip().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new Errors.Validation("EMAIL_INVALID");
        if (admins.findByEmailIgnoreCase(email.strip()).isPresent()) throw new Errors.Validation("EMAIL_TAKEN");
        validatePassword(tempPassword);
        Admin a = admins.save(new Admin(email.strip(), encoder.encode(tempPassword), role == null ? Role.SUPPORT : role, true));
        audit.record(Actor.ADMIN, by.email(), null, "ADMIN_CREATED", "ADMIN", a.id, "role=" + a.role);
        return a;
    }

    @Transactional
    public Admin setStatus(Principal by, long adminId, Status status) {
        requireSuper(by);
        if (by.id() == adminId) throw new Errors.Validation("CANNOT_CHANGE_SELF");
        Admin a = admins.findById(adminId).orElseThrow(() -> new Errors.NotFound("admin"));
        a.status = status;
        admins.save(a);
        if (status == Status.DISABLED) sessions.revokeAll(adminId);
        audit.record(Actor.ADMIN, by.email(), null, "ADMIN_STATUS_" + status, "ADMIN", adminId, null);
        return a;
    }

    @Transactional
    public Admin reset2fa(Principal by, long adminId) {
        requireSuper(by);
        Admin a = admins.findById(adminId).orElseThrow(() -> new Errors.NotFound("admin"));
        a.totpEnabled = false;
        a.totpSecret = null;
        admins.save(a);
        sessions.revokeAll(adminId);
        audit.record(Actor.ADMIN, by.email(), null, "ADMIN_2FA_RESET", "ADMIN", adminId, null);
        return a;
    }

    public static void requireSuper(Principal p) {
        if (p == null || !p.isSuperadmin()) throw new Errors.Forbidden("SUPERADMIN_REQUIRED");
    }

    static void validatePassword(String p) {
        if (p == null || p.length() < MIN_PASSWORD) throw new Errors.Validation("PASSWORD_TOO_SHORT");
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT10M")
    @Transactional
    public void purgeSessions() {
        sessions.purgeOld();
    }

    // ================================================================= helpers

    private String newSession(Long adminId, String kind, Instant expires) {
        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.save(new AdminSession(hash(token), adminId, kind, expires));
        return token;
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Principal principal(Admin a) {
        return new Principal(a.id, a.email, a.role);
    }
}
