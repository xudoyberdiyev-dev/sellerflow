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
    /** Sahifa raqami chegarasi (int overflow va ma'nosiz offsetlar o'rniga 400). */
    static final int MAX_PAGE = 100_000;

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
    private final uz.sellerflow.ops.BackupMonitor backup;
    private final DashboardService dashboard;

    public AdminController(JdbcTemplate jdbc, AccountService accounts, SupportService support, AuditService audit,
                           AdminAuthService auth, AccessGrantService grants, BotStatus bot, ObjectMapper mapper,
                           LoginRateLimiter loginLimiter, uz.sellerflow.ops.BackupMonitor backup,
                           DashboardService dashboard) {
        this.backup = backup;
        this.dashboard = dashboard;
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
    public Map<String, Object> current(@RequestAttribute(PRINCIPAL) Principal p, HttpServletRequest req) {
        Map<String, Object> r = me(p);
        r.put("expiresAt", str(auth.sessionExpiresAt(bearer(req))));
        return r;
    }

    @GetMapping("/auth/sessions")
    public List<Map<String, Object>> sessions(@RequestAttribute(PRINCIPAL) Principal p, HttpServletRequest req) {
        return auth.activeSessions(p, bearer(req)).stream()
                .map(s -> m("createdAt", str(s.createdAt()), "expiresAt", str(s.expiresAt()), "current", s.current())).toList();
    }

    @PostMapping("/auth/sessions/revoke-others")
    public Map<String, Object> revokeOthers(@RequestAttribute(PRINCIPAL) Principal p, HttpServletRequest req) {
        return m("revoked", auth.revokeOtherSessions(p, bearer(req)));
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

    /** D-5: davr (7/30/90 kun) bo'yicha KPI, kunlik seriyalar, kanallar, onboarding voronkasi, 7 kunlik retention. */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(defaultValue = "30") int period) {
        return dashboard.dashboard(period);
    }

    /**
     * D-5: bildirishnomalar — so'nggi muhim hodisalar (audit asosida). SUPERADMIN hammasini,
     * SUPPORT faqat o'z ruxsat so'rovlariga oid hodisalarni ko'radi.
     */
    @GetMapping("/notifications")
    public Map<String, Object> notifications(@RequestAttribute(PRINCIPAL) Principal p,
                                             @RequestParam(defaultValue = "20") int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        String cols = "select e.id, e.created_at as \"at\", e.action, e.actor_type as \"actorType\", e.actor_id as \"actorId\","
                + " e.entity_type as \"entityType\", e.entity_id as \"entityId\", e.summary from audit_events e";
        List<Map<String, Object>> items;
        if (p.isSuperadmin()) {
            List<Object> args = new java.util.ArrayList<>(NOTIFY_SUPER);
            args.add(limit);
            items = jdbc.queryForList(cols + " where e.action in (" + String.join(",", java.util.Collections.nCopies(
                    NOTIFY_SUPER.size(), "?")) + ") order by e.id desc limit ?", args.toArray());
        } else {
            items = jdbc.queryForList(cols + " join access_grants g on e.entity_type = 'ACCESS_GRANT' and e.entity_id = g.id::text"
                    + " where g.admin_id = ? and e.action in ('ACCESS_APPROVED','ACCESS_DECLINED','ACCESS_REVOKED')"
                    + " order by e.id desc limit ?", p.id(), limit);
        }
        return m("items", items, "backupState", backup.read().state().name());
    }

    static final List<String> NOTIFY_SUPER = List.of("ACCESS_APPROVED", "ACCESS_DECLINED", "ACCESS_REVOKED",
            "ADMIN_LOCKED", "ADMIN_CREATED", "ADMIN_RECOVERED", "ADMIN_STATUS_CHANGE", "DELETION_REQUESTED",
            "ACCOUNT_CLOSED", "SUPPORT_UPDATED", "EXPORT_SELLERS", "EXPORT_AUDIT");

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean dbOk;
        Long dbLatencyMs = null;
        long t0 = System.nanoTime();
        try {
            dbOk = Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class));
            dbLatencyMs = (System.nanoTime() - t0) / 1_000_000;
        } catch (RuntimeException e) {
            dbOk = false;
        }
        Instant lastPoll = bot.getLastPollOkAt();
        boolean botOk = bot.isPollingEnabled() && lastPoll != null && lastPoll.isAfter(Instant.now().minusSeconds(120));
        return m("database", dbOk ? "UP" : "DOWN", "dbLatencyMs", dbLatencyMs,
                "bot", !bot.isPollingEnabled() ? "DISABLED" : botOk ? "UP" : "DEGRADED",
                "lastPollOkAt", str(lastPoll), "lastErrorAt", str(bot.getLastErrorAt()), "lastError", bot.getLastError(),
                "processedUpdates", bot.getProcessedUpdates(),
                "activeDrafts", count("select count(*) from telegram_flows where flow_type is not null"),
                "engineVersion", CalculationEngine.VERSION,
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "serverTime", Instant.now().toString(),
                "backup", backupView(backup.read()));
    }

    private static Map<String, Object> backupView(uz.sellerflow.ops.BackupMonitor.Status b) {
        return m("state", b.state().name(), "lastSuccessAt", str(b.lastSuccessAt()), "lastSuccessFile", b.lastSuccessFile(),
                "lastFailureAt", str(b.lastFailureAt()), "lastError", b.lastError(), "consecutiveFailures", b.consecutiveFailures());
    }

    // ================================================================= sellers

    /** Sotuvchilar ro'yxati: faqat metadata (holat, kanal, tarif, sanalar). */
    /**
     * D-5: sotuvchi kartasi — faqat metadata va sonlar (tijoriy qiymatlar yo'q; ular faqat grant orqali).
     * SUPPORT ham o'qiy oladi (ro'yxat kabi).
     */
    @GetMapping("/sellers/{telegramUserId}")
    public Map<String, Object> seller(@PathVariable long telegramUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.id as "userId", u.telegram_user_id as "telegramUserId", u.status, u.created_at as "createdAt",
                       u.deletion_requested_at as "deletionRequestedAt",
                       b.id as "businessId", b.primary_channel as "primaryChannel", b.plan_code as "planCode",
                       b.created_at as "activatedAt", b.business_name as "businessName", b.display_name as "displayName"
                from users u left join businesses b on b.owner_user_id = u.id where u.telegram_user_id = ?""", telegramUserId);
        if (rows.isEmpty()) throw new Errors.NotFound("seller");
        Map<String, Object> r = new LinkedHashMap<>(rows.get(0));
        long userId = ((Number) r.remove("userId")).longValue();
        Object bid = r.get("businessId");
        List<Map<String, Object>> consent = jdbc.queryForList(
                "select terms_version as \"version\", accepted_at as \"acceptedAt\" from consents where user_id = ? order by accepted_at desc limit 1",
                userId);
        r.put("terms", consent.isEmpty() ? null : consent.get(0));
        if (bid != null) {
            long b = ((Number) bid).longValue();
            r.put("productCount", count("select count(*) from products where business_id = " + b + " and status = 'ACTIVE'"));
            r.put("savedCalculationCount", count("select count(*) from calculation_history where business_id = " + b));
            r.put("lastActivityAt", jdbc.queryForObject("""
                    select greatest((select max(updated_at) from products where business_id = ?),
                                    (select max(saved_at) from calculation_history where business_id = ?))""",
                    java.sql.Timestamp.class, b, b));
        } else {
            r.put("productCount", 0L);
            r.put("savedCalculationCount", 0L);
            r.put("lastActivityAt", null);
        }
        List<String> reason = jdbc.queryForList("""
                select summary from audit_events where action = 'ADMIN_STATUS_CHANGE' and entity_type = 'USER'
                  and entity_id = ? order by id desc limit 1""", String.class, String.valueOf(userId));
        r.put("lastStatusChange", reason.isEmpty() ? null : reason.get(0));
        return r;
    }

    @GetMapping("/sellers")
    public Map<String, Object> sellers(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long telegramUserId,
                                       @RequestParam(required = false) String channel,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(defaultValue = "created_desc") String sort) {
        int limit = Math.min(Math.max(size, 1), 200);
        if (page < 0 || page > MAX_PAGE) throw new Errors.Validation("PAGE_RANGE");
        String order = switch (sort) {
            case "created_desc" -> " order by u.created_at desc, u.id desc";
            case "created_asc" -> " order by u.created_at asc, u.id asc";
            default -> throw new Errors.Validation("SORT_INVALID");
        };
        // D-5: status dan tashqari filtrlar (holatlar bo'yicha sonlar shu filtrlar bilan hisoblanadi)
        StringBuilder base = new StringBuilder(" where 1=1");
        List<Object> baseArgs = new java.util.ArrayList<>();
        if (telegramUserId != null) {
            base.append(" and u.telegram_user_id = ?");
            baseArgs.add(telegramUserId);
        }
        if (channel != null && !channel.isBlank()) {
            base.append(" and b.primary_channel = ?");
            baseArgs.add(uz.sellerflow.account.AccountEntities.SalesChannel.valueOf(channel).name());
        }
        java.time.ZoneId tz = java.time.ZoneId.of("Asia/Tashkent");
        if (from != null && !from.isBlank()) {
            base.append(" and u.created_at >= ?");
            baseArgs.add(java.sql.Timestamp.from(day(from).atStartOfDay(tz).toInstant()));
        }
        if (to != null && !to.isBlank()) {
            base.append(" and u.created_at < ?");
            baseArgs.add(java.sql.Timestamp.from(day(to).plusDays(1).atStartOfDay(tz).toInstant()));
        }
        StringBuilder where = new StringBuilder(base);
        List<Object> args = new java.util.ArrayList<>(baseArgs);
        if (status != null && !status.isBlank()) {
            List<String> st = new java.util.ArrayList<>();
            for (String x : status.split(",")) st.add(AccountStatus.valueOf(x.strip()).name());
            where.append(" and u.status in (").append(String.join(",", java.util.Collections.nCopies(st.size(), "?"))).append(")");
            args.addAll(st);
        }
        String fromSql = " from users u left join businesses b on b.owner_user_id = u.id";
        long total = jdbc.queryForObject("select count(*)" + fromSql + where, Long.class, args.toArray());
        Map<String, Object> counts = new LinkedHashMap<>();
        for (AccountStatus st : AccountStatus.values()) counts.put(st.name(), 0L);
        jdbc.query("select u.status, count(*)" + fromSql + base + " group by u.status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, baseArgs.toArray());
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add((long) page * limit);
        List<Map<String, Object>> items = jdbc.queryForList("""
                select u.telegram_user_id as "telegramUserId", u.status, u.created_at as "createdAt",
                       u.deletion_requested_at as "deletionRequestedAt",
                       b.id as "businessId", b.primary_channel as "primaryChannel", b.plan_code as "planCode",
                       b.created_at as "activatedAt"
                """ + fromSql + where + order + " limit ? offset ?", pageArgs.toArray());
        return m("total", total, "page", page, "size", limit, "counts", counts, "items", items);
    }

    private static java.time.LocalDate day(String v) {
        try {
            return java.time.LocalDate.parse(v.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new Errors.Validation("DATE_INVALID");
        }
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
    public List<Map<String, Object>> listGrants(@RequestAttribute(PRINCIPAL) Principal p,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long telegramUserId) {
        // D-5: sotuvchi filtri (DB darajasida) va holat tablari (samarali holat bo'yicha)
        Long businessId = null;
        if (telegramUserId != null) {
            List<Long> b = jdbc.queryForList("select b.id from businesses b join users u on u.id = b.owner_user_id"
                    + " where u.telegram_user_id = ?", Long.class, telegramUserId);
            if (b.isEmpty()) return List.of();
            businessId = b.get(0);
        }
        java.util.Set<String> statuses = new java.util.HashSet<>();
        if (status != null && !status.isBlank()) {
            for (String x : status.split(",")) statuses.add(AccessGrantService.Status.valueOf(x.strip()).name());
        }
        // LOW (DB): N+1 o'rniga ikki so'rov — sotuvchi Telegram ID'lari va admin email'lari bir yo'la
        List<Grant> list = grants.listFor(p, businessId).stream()
                .filter(g -> statuses.isEmpty() || statuses.contains(grants.statusOf(g).name())).toList();
        if (list.isEmpty()) return List.of();
        Long[] bids = list.stream().map(Grant::getBusinessId).distinct().toArray(Long[]::new);
        Long[] aids = list.stream().map(Grant::getAdminId).distinct().toArray(Long[]::new);
        Map<Long, Long> tg = new java.util.HashMap<>();
        jdbc.query("select b.id, u.telegram_user_id from businesses b join users u on u.id = b.owner_user_id where b.id = any(?)",
                rs -> { tg.put(rs.getLong(1), rs.getLong(2)); }, (Object) bids);
        Map<Long, String> emails = new java.util.HashMap<>();
        jdbc.query("select id, email from admins where id = any(?)", rs -> { emails.put(rs.getLong(1), rs.getString(2)); },
                (Object) aids);
        return list.stream().map(g -> grantView(g, tg.get(g.getBusinessId()), emails.get(g.getAdminId()))).toList();
    }

    @PostMapping("/grants/{id}/revoke")
    public Map<String, Object> revokeGrant(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id) {
        return grantView(grants.revokeByAdmin(p, id));
    }

    /** READ-ONLY ko'rinish. Har chaqiruv auditga yoziladi. */
    @GetMapping("/grants/{id}/data")
    public Map<String, Object> grantData(@RequestAttribute(PRINCIPAL) Principal p, @PathVariable long id,
                                         @RequestParam(defaultValue = "0") int productPage,
                                         @RequestParam(defaultValue = "0") int historyPage) {
        if (productPage < 0 || productPage > MAX_PAGE || historyPage < 0 || historyPage > MAX_PAGE) {
            throw new Errors.Validation("PAGE_RANGE");
        }
        var v = grants.view(p, id, productPage, historyPage);
        return m("grant", grantView(v.grant()),
                "productPage", m("page", productPage, "total", v.productTotal(), "totalPages", v.productPages()),
                "historyPage", m("page", historyPage, "total", v.historyTotal(), "totalPages", v.historyPages()),
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
        return grantView(g, tgId, adminEmail);
    }

    private Map<String, Object> grantView(Grant g, Long tgId, String adminEmail) {
        return m("id", g.getId(), "telegramUserId", tgId, "adminEmail", adminEmail, "purpose", g.getPurpose(),
                "minutes", g.getMinutes(), "status", grants.statusOf(g).name(), "requestedAt", str(g.getRequestedAt()),
                "decidedAt", str(g.getDecidedAt()), "expiresAt", str(g.getExpiresAt()), "revokedBy", g.getRevokedBy());
    }

    // ================================================================= support & audit (SUPERADMIN)

    @GetMapping("/support")
    public Map<String, Object> getSupport() {
        return supportView(support.settings());
    }

    /** D-5: «oxirgi o'zgartirgan admin/vaqt» — audit'dagi oxirgi SUPPORT_UPDATED dan. */
    private Map<String, Object> supportView(SupportService.SupportSettings s) {
        List<Map<String, Object>> last = jdbc.queryForList("select actor_id as \"by\", created_at as \"at\" from audit_events"
                + " where action = 'SUPPORT_UPDATED' order by id desc limit 1");
        Object at = last.isEmpty() ? null : last.get(0).get("at");
        return m("enabled", s.isEnabled(), "label", s.getContactLabel(), "url", s.getContactUrl(),
                "updatedBy", last.isEmpty() ? null : last.get(0).get("by"),
                "updatedAt", at instanceof java.sql.Timestamp t ? t.toInstant().toString() : null);
    }

    @PutMapping("/support")
    public Map<String, Object> putSupport(@RequestAttribute(PRINCIPAL) Principal p, @RequestBody SupportUpdate body) {
        AdminAuthService.requireSuper(p);
        return supportView(support.update(body.enabled(), body.label(), body.url(), p.email()));
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestAttribute(PRINCIPAL) Principal p,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String actor,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) Long businessId) {
        AdminAuthService.requireSuper(p);
        if (page < 0 || page > MAX_PAGE) throw new Errors.Validation("PAGE_RANGE");
        int limit = Math.min(Math.max(size, 1), 100);
        List<Object> args = new java.util.ArrayList<>();
        String where = auditWhere(from, to, actor, action, businessId, args);
        long total = jdbc.queryForObject("select count(*) from audit_events" + where, Long.class, args.toArray());
        args.add(limit);
        args.add((long) page * limit);
        return m("total", total, "page", page, "items", jdbc.queryForList(AUDIT_COLS + where + " order by id desc limit ? offset ?",
                args.toArray()));
    }

    private static final String AUDIT_COLS = """
            select id, created_at as "at", actor_type as "actorType", actor_id as "actorId", business_id as "businessId",
                   action, entity_type as "entityType", entity_id as "entityId", summary, source_ip as "sourceIp"
            from audit_events""";

    /** D-5: audit filtrlari (sana — Asia/Tashkent kunlari, bajaruvchi, amal, biznes). */
    private static String auditWhere(String from, String to, String actor, String action, Long businessId, List<Object> args) {
        StringBuilder w = new StringBuilder(" where 1=1");
        java.time.ZoneId tz = java.time.ZoneId.of("Asia/Tashkent");
        if (from != null && !from.isBlank()) {
            w.append(" and created_at >= ?");
            args.add(java.sql.Timestamp.from(day(from).atStartOfDay(tz).toInstant()));
        }
        if (to != null && !to.isBlank()) {
            w.append(" and created_at < ?");
            args.add(java.sql.Timestamp.from(day(to).plusDays(1).atStartOfDay(tz).toInstant()));
        }
        if (actor != null && !actor.isBlank()) {
            w.append(" and lower(actor_id) = lower(?)");
            args.add(actor.strip());
        }
        if (action != null && !action.isBlank()) {
            w.append(" and action = ?");
            args.add(action.strip());
        }
        if (businessId != null) {
            w.append(" and business_id = ?");
            args.add(businessId);
        }
        return w.toString();
    }

    static final int EXPORT_MAX_ROWS = 10_000;

    /** D-5: audit CSV eksporti (faqat SUPERADMIN), eksportning o'zi ham auditga yoziladi. */
    @GetMapping(value = "/audit/export.csv", produces = "text/csv")
    public ResponseEntity<String> auditCsv(@RequestAttribute(PRINCIPAL) Principal p,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String actor,
                                           @RequestParam(required = false) String action,
                                           @RequestParam(required = false) Long businessId) {
        AdminAuthService.requireSuper(p);
        List<Object> args = new java.util.ArrayList<>();
        String where = auditWhere(from, to, actor, action, businessId, args);
        args.add(EXPORT_MAX_ROWS);
        List<Map<String, Object>> rows = jdbc.queryForList(AUDIT_COLS + where + " order by id desc limit ?", args.toArray());
        audit.recordStandalone(AuditService.Actor.ADMIN, p.email(), null, "EXPORT_AUDIT", "AUDIT", null, "rows=" + rows.size());
        return csv("audit.csv", rows);
    }

    /** D-5: sotuvchilar CSV eksporti — faqat metadata (tijoriy qiymatlar yo'q), auditga yoziladi. */
    @GetMapping(value = "/sellers/export.csv", produces = "text/csv")
    public ResponseEntity<String> sellersCsv(@RequestAttribute(PRINCIPAL) Principal p) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.telegram_user_id as "telegramUserId", u.status, u.created_at as "createdAt",
                       b.primary_channel as "primaryChannel", b.plan_code as "planCode", b.created_at as "activatedAt"
                from users u left join businesses b on b.owner_user_id = u.id where u.telegram_user_id > 0
                order by u.id desc limit ?""", EXPORT_MAX_ROWS);
        audit.recordStandalone(AuditService.Actor.ADMIN, p.email(), null, "EXPORT_SELLERS", "USER", null, "rows=" + rows.size());
        return csv("sellers.csv", rows);
    }

    private static ResponseEntity<String> csv(String name, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF"); // Excel uchun UTF-8 BOM
        if (!rows.isEmpty()) {
            sb.append(String.join(",", rows.get(0).keySet().stream().map(AdminController::csvCell).toList())).append("\r\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.join(",", r.values().stream().map(v -> csvCell(v == null ? "" : v.toString())).toList()))
                        .append("\r\n");
            }
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(sb.toString());
    }

    /** CSV injection himoyasi: = + - @ (va tab/CR) bilan boshlangan qiymat oldiga ' qo'shiladi. */
    static String csvCell(String v) {
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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
