package uz.sellerflow.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.sellerflow.access.AccessGrantService;
import uz.sellerflow.access.AccessGrantService.Grant;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.account.AccountService;
import uz.sellerflow.admin.AdminAuthService.Admin;
import uz.sellerflow.admin.AdminAuthService.LoginResult;
import uz.sellerflow.admin.AdminAuthService.Principal;
import uz.sellerflow.admin.AdminAuthService.Role;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.calculation.CalculationEngine;
import uz.sellerflow.common.Errors;
import uz.sellerflow.support.SupportService;
import uz.sellerflow.telegram.BotStatus;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Superadmin API (PRD-07 V1 minimal). Autentifikatsiya: parol + TOTP -> Bearer sessiya tokeni.
 * Rollar: SUPERADMIN (hammasi), SUPPORT (statistika, sotuvchilar ro'yxati, o'z kirish so'rovlari).
 * Sotuvchining tijoriy ma'lumotlari faqat /grants/{id}/data orqali, sotuvchi roziligi bilan qaytariladi.
 */
@RestController
@RequestMapping("/admin/api/v1")
public class AdminController {

    static final String PRINCIPAL = "sf.admin";

    public record LoginRequest(String email, String password, String code) {}
    public record TotpConfirm(String setupToken, String code) {}
    public record PasswordChange(String currentPassword, String newPassword) {}
    public record NewAdmin(String email, String role, String tempPassword) {}
    public record StatusChange(String reason) {}
    public record SupportUpdate(boolean enabled, String label, String url) {}
    public record GrantRequest(Long telegramUserId, String purpose, Integer minutes) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final SupportService support;
    private final AuditService audit;
    private final AdminAuthService auth;
    private final AccessGrantService grants;
    private final BotStatus bot;
    private final ObjectMapper mapper;
    private final LoginRateLimiter loginLimiter;

    public AdminController(JdbcTemplate jdbc, AccountService accounts, SupportService support, AuditService audit,
                           AdminAuthService auth, AccessGrantService grants, BotStatus bot, ObjectMapper mapper,
                           LoginRateLimiter loginLimiter) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.support = support;
        this.audit = audit;
        this.auth = auth;
        this.grants = grants;
        this.bot = bot;
        this.mapper = mapper;
        this.loginLimiter = loginLimiter;
    }

    // ================================================================= auth (ochiq endpointlar)

    @PublicAdminEndpoint
    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest r, HttpServletRequest req) {
        return limited(req, () -> auth.login(r.email(), r.password(), r.code()));
    }

    @PublicAdminEndpoint
    @PostMapping("/auth/totp/confirm")
    public Map<String, Object> confirm(@RequestBody TotpConfirm r, HttpServletRequest req) {
        return limited(req, () -> auth.confirmTotp(r.setupToken(), r.code()));
    }

    /** M-2: IP bo'yicha limit — limitdan oshgan IP uchun parol umuman tekshirilmaydi (429). */
    private Map<String, Object> limited(HttpServletRequest req, java.util.function.Supplier<LoginResult> attempt) {
        String ip = req.getRemoteAddr();
        if (loginLimiter.isBlocked(ip)) throw new TooManyAttempts();
        try {
            return loginView(attempt.get());
        } catch (Errors.Forbidden e) {
            loginLimiter.recordFailure(ip);
            throw e;
        }
    }

    static class TooManyAttempts extends RuntimeException {
        TooManyAttempts() { super("TOO_MANY_ATTEMPTS"); }
    }

    @ExceptionHandler(TooManyAttempts.class)
    public ResponseEntity<Map<String, Object>> tooMany(TooManyAttempts e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(LoginRateLimiter.WINDOW.toSeconds()))
                .body(m("error", "TOO_MANY_ATTEMPTS"));
    }

    private static Map<String, Object> loginView(LoginResult r) {
        return m("status", r.status(), "token", r.token(), "setupToken", r.setupToken(), "totpSecret", r.totpSecret(),
                "otpauthUri", r.otpauthUri(), "admin", r.admin() == null ? null : me(r.admin()),
                "mustChangePassword", r.mustChangePassword());
    }

    @AllowedWithTempPassword
    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        auth.logout(bearer(req));
        return m("ok", true);
    }

    @AllowedWithTempPassword
    @GetMapping("/auth/me")
    public Map<String, Object> current(@RequestAttribute(PRINCIPAL) Principal p) {
        return me(p);
    }

    @AllowedWithTempPassword
    @PostMapping("/auth/password")
    public Map<String, Object> password(@RequestAttribute(PRINCIPAL) Principal p, @RequestBody PasswordChange r,
                                        HttpServletRequest req) {
        auth.changePassword(p, bearer(req), r.currentPassword(), r.newPassword());
        return m("ok", true);
    }

    private static Map<String, Object> me(Principal p) {
        return m("id", p.id(), "email", p.email(), "role", p.role().name(), "mustChangePassword", p.mustChangePassword());
    }

    // ================================================================= dashboard

    /** Faqat agregat sonlar — tijoriy qiymatlar yo'q. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (AccountStatus s : AccountStatus.values()) {
            byStatus.put(s.name(), jdbc.queryForObject("select count(*) from users where status = ?", Long.class, s.name()));
        }
        return m(
                "usersByStatus", byStatus,
                "businesses", count("select count(*) from businesses"),
                "newSellersLast7Days", count("select count(*) from businesses where created_at > now() - interval '7 days'"),
                "activeProducts", count("select count(*) from products where status = 'ACTIVE'"),
                "savedCalculations", count("select count(*) from calculation_history"),
                "savedCalculationsLast7Days", count("select count(*) from calculation_history where saved_at > now() - interval '7 days'"),
                "partialEstimateShare", jdbc.queryForObject(
                        "select coalesce(round(100.0 * avg(case when accuracy_status = 'PARTIAL_ESTIMATE' then 1 else 0 end), 1), 0) from calculation_history",
                        Double.class),
                "channels", jdbc.queryForList("select primary_channel as channel, count(*) as count from businesses group by 1 order by 2 desc"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean dbOk;
        try {
            dbOk = Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class));
        } catch (RuntimeException e) {
            dbOk = false;
        }
        Instant lastPoll = bot.getLastPollOkAt();
        boolean botOk = bot.isPollingEnabled() && lastPoll != null && lastPoll.isAfter(Instant.now().minusSeconds(120));
        return m("database", dbOk ? "UP" : "DOWN",
                "bot", !bot.isPollingEnabled() ? "DISABLED" : botOk ? "UP" : "DEGRADED",
                "lastPollOkAt", str(lastPoll), "lastErrorAt", str(bot.getLastErrorAt()), "lastError", bot.getLastError(),
                "processedUpdates", bot.getProcessedUpdates(),
                "activeDrafts", count("select count(*) from telegram_flows where flow_type is not null"),
                "engineVersion", CalculationEngine.VERSION,
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "serverTime", Instant.now().toString());
    }

    // ================================================================= sellers

    /** Sotuvchilar ro'yxati: faqat metadata (holat, kanal, tarif, sanalar). */
    @GetMapping("/sellers")
    public Map<String, Object> sellers(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long telegramUserId) {
        int limit = Math.min(Math.max(size, 1), 200);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            AccountStatus.valueOf(status);
            where.append(" and u.status = ?");
            args.add(status);
        }
        if (telegramUserId != null) {
            where.append(" and u.telegram_user_id = ?");
            args.add(telegramUserId);
        }
        long total = jdbc.queryForObject("select count(*) from users u" + where, Long.class, args.toArray());
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(Math.max(page, 0) * limit);
        List<Map<String, Object>> items = jdbc.queryForList("""
                select u.telegram_user_id as "telegramUserId", u.status, u.created_at as "createdAt",
                       u.deletion_requested_at as "deletionRequestedAt",
                       b.id as "businessId", b.primary_channel as "primaryChannel", b.plan_code as "planCode",
                       b.created_at as "activatedAt"
                from users u left join businesses b on b.owner_user_id = u.id""" + where + " order by u.id desc limit ? offset ?",
                pageArgs.toArray());
        return m("total", total, "page", page, "size", limit, "items", items);
    }

    @PostMapping("/sellers/{telegramUserId}/suspend")
    public Map<String, Object> suspend(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long telegramUserId,
                                       @RequestBody(required = false) StatusChange body) {
        AdminAuthService.requireSuper(p);
        var u = accounts.adminSetStatus(telegramUserId, AccountStatus.SUSPENDED, p.email(), body == null ? null : body.reason());
        return m("telegramUserId", telegramUserId, "status", u.getStatus().name());
    }

    /** SUSPENDED yoki DELETION_REQUESTED (kutish davrida) -> ACTIVE. */
    @PostMapping("/sellers/{telegramUserId}/reactivate")
    public Map<String, Object> reactivate(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long telegramUserId,
                                          @RequestBody(required = false) StatusChange body) {
        AdminAuthService.requireSuper(p);
        var u = accounts.adminSetStatus(telegramUserId, AccountStatus.ACTIVE, p.email(), body == null ? null : body.reason());
        return m("telegramUserId", telegramUserId, "status", u.getStatus().name());
    }

    // ================================================================= access grants

    @PostMapping("/grants")
    public Map<String, Object> requestGrant(@RequestAttribute(PRINCIPAL) Principal p, @RequestBody GrantRequest r) {
        if (r.telegramUserId() == null) throw new Errors.Validation("TELEGRAM_USER_ID_REQUIRED");
        Grant g = grants.request(p, r.telegramUserId(), r.purpose(), r.minutes() == null ? 30 : r.minutes());
        return grantView(g);
    }

    @GetMapping("/grants")
    public List<Map<String, Object>> listGrants(@RequestAttribute(PRINCIPAL) Principal p) {
        return grants.listFor(p).stream().map(this::grantView).toList();
    }

    @PostMapping("/grants/{id}/revoke")
    public Map<String, Object> revokeGrant(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        return grantView(grants.revokeByAdmin(p, id));
    }

    /** READ-ONLY ko'rinish. Har chaqiruv auditga yoziladi. */
    @GetMapping("/grants/{id}/data")
    public Map<String, Object> grantData(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        var v = grants.view(p, id);
        return m("grant", grantView(v.grant()),
                "products", v.products().stream().map(x -> m("id", x.getId(), "name", x.getName(),
                        "unitCost", x.getUnitCost(), "plannedSalePrice", x.getPlannedSalePrice(), "sku", x.getSku())).toList(),
                "history", v.history().stream().map(h -> m("id", h.getId(), "type", h.getType().name(),
                        "productName", h.getProductNameSnapshot(), "channel", h.getChannelSnapshot(),
                        "accuracy", h.getAccuracyStatus(), "savedAt", h.getSavedAt().toString(),
                        "input", json(h.getInputSnapshot()), "expenses", json(h.getExpenseSnapshot()),
                        "output", json(h.getOutputSnapshot()))).toList());
    }

    private Map<String, Object> grantView(Grant g) {
        Long tgId = jdbc.query("select u.telegram_user_id from businesses b join users u on u.id = b.owner_user_id where b.id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, g.getBusinessId());
        String adminEmail = jdbc.query("select email from admins where id = ?", rs -> rs.next() ? rs.getString(1) : null, g.getAdminId());
        return m("id", g.getId(), "telegramUserId", tgId, "adminEmail", adminEmail, "purpose", g.getPurpose(),
                "minutes", g.getMinutes(), "status", grants.statusOf(g).name(), "requestedAt", str(g.getRequestedAt()),
                "decidedAt", str(g.getDecidedAt()), "expiresAt", str(g.getExpiresAt()), "revokedBy", g.getRevokedBy());
    }

    // ================================================================= support & audit (SUPERADMIN)

    @GetMapping("/support")
    public Map<String, Object> getSupport() {
        var s = support.settings();
        return m("enabled", s.isEnabled(), "label", s.getContactLabel(), "url", s.getContactUrl());
    }

    @PutMapping("/support")
    public Map<String, Object> putSupport(@RequestAttribute(PRINCIPAL) Principal p, @RequestBody SupportUpdate body) {
        AdminAuthService.requireSuper(p);
        var s = support.update(body.enabled(), body.label(), body.url(), p.email());
        return m("enabled", s.isEnabled(), "label", s.getContactLabel(), "url", s.getContactUrl());
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestAttribute(PRINCIPAL) Principal p,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        AdminAuthService.requireSuper(p);
        var pg = audit.list(page, size);
        return m("total", pg.getTotalElements(), "page", page, "items", pg.map(e -> m(
                "id", e.getId(), "at", e.getCreatedAt().toString(), "actorType", e.getActorType(),
                "actorId", e.getActorId(), "businessId", e.getBusinessId(), "action", e.getAction(),
                "entityType", e.getEntityType(), "entityId", e.getEntityId(), "summary", e.getSummary())).getContent());
    }

    // ================================================================= admins (SUPERADMIN)

    @GetMapping("/admins")
    public List<Map<String, Object>> admins(@RequestAttribute(PRINCIPAL) Principal p) {
        AdminAuthService.requireSuper(p);
        return auth.list().stream().map(AdminController::adminView).toList();
    }

    @PostMapping("/admins")
    public Map<String, Object> createAdmin(@RequestAttribute(PRINCIPAL) Principal p, @RequestBody NewAdmin r) {
        Role role = r.role() == null ? Role.SUPPORT : Role.valueOf(r.role());
        return adminView(auth.create(p, r.email(), role, r.tempPassword()));
    }

    @PostMapping("/admins/{id}/disable")
    public Map<String, Object> disable(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        return adminView(auth.setStatus(p, id, AdminAuthService.Status.DISABLED));
    }

    @PostMapping("/admins/{id}/enable")
    public Map<String, Object> enable(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        return adminView(auth.setStatus(p, id, AdminAuthService.Status.ACTIVE));
    }

    @PostMapping("/admins/{id}/reset-2fa")
    public Map<String, Object> reset2fa(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        return adminView(auth.reset2fa(p, id));
    }

    private static Map<String, Object> adminView(Admin a) {
        return m("id", a.getId(), "email", a.getEmail(), "role", a.getRole().name(), "status", a.getStatus().name(),
                "totpEnabled", a.isTotpEnabled(), "mustChangePassword", a.isMustChangePassword(),
                "createdAt", str(a.getCreatedAt()), "lastLoginAt", str(a.getLastLoginAt()));
    }

    // ================================================================= errors & helpers

    @ExceptionHandler(Errors.NotFound.class)
    public ResponseEntity<Map<String, Object>> notFound(Errors.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(m("error", "NOT_FOUND"));
    }

    @ExceptionHandler(Errors.Validation.class)
    public ResponseEntity<Map<String, Object>> validation(Errors.Validation e) {
        return ResponseEntity.badRequest().body(m("error", e.code()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badArg(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(m("error", "BAD_REQUEST"));
    }

    @ExceptionHandler(Errors.Forbidden.class)
    public ResponseEntity<Map<String, Object>> forbidden(Errors.Forbidden e) {
        String code = e.getMessage();
        HttpStatus st = switch (code) {
            case "INVALID_CREDENTIALS", "INVALID_CODE", "INVALID_SESSION", "LOCKED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(st).body(m("error", code));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private JsonNode json(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String str(Instant i) { return i == null ? null : i.toString(); }

    static String bearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).strip() : null;
    }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }
}
