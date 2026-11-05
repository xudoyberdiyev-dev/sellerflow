package uz.sellerflow.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.account.AccountService;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.common.Errors;
import uz.sellerflow.common.SellerFlowProperties;
import uz.sellerflow.support.SupportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Minimal Superadmin API (V1.0 Closed Beta). To'liq Web Superadmin — PRD-07 dan keyin.
 * Himoya: X-Admin-Key sarlavhasi (ADMIN_API_KEY). Kalit berilmagan bo'lsa API butunlay yopiq.
 * Sotuvchining tijoriy ma'lumotlari (tannarx, foyda, tarix) bu yerda QAYTARILMAYDI (SF-PRD-01, 5.4).
 */
@RestController
@RequestMapping("/admin/api/v1")
public class AdminController {

    public record StatusChange(String reason) {}

    public record SupportUpdate(boolean enabled, String label, String url) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final SupportService support;
    private final AuditService audit;

    public AdminController(JdbcTemplate jdbc, AccountService accounts, SupportService support, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.support = support;
        this.audit = audit;
    }

    /** Faqat agregat sonlar — tijoriy qiymatlar yo'q. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> byStatus = new java.util.LinkedHashMap<>();
        for (AccountStatus s : AccountStatus.values()) {
            byStatus.put(s.name(), jdbc.queryForObject("select count(*) from users where status = ?", Long.class, s.name()));
        }
        return Map.of(
                "usersByStatus", byStatus,
                "businesses", jdbc.queryForObject("select count(*) from businesses", Long.class),
                "activeProducts", jdbc.queryForObject("select count(*) from products where status = 'ACTIVE'", Long.class),
                "savedCalculations", jdbc.queryForObject("select count(*) from calculation_history", Long.class),
                "savedCalculationsLast7Days", jdbc.queryForObject(
                        "select count(*) from calculation_history where saved_at > now() - interval '7 days'", Long.class));
    }

    /** Sotuvchilar ro'yxati: faqat metadata (holat, kanal, tarif, sana). */
    @GetMapping("/sellers")
    public List<Map<String, Object>> sellers(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        int limit = Math.min(Math.max(size, 1), 200);
        return jdbc.queryForList("""
                select u.telegram_user_id, u.status, u.created_at, u.deletion_requested_at,
                       b.id as business_id, b.primary_channel, b.plan_code
                from users u left join businesses b on b.owner_user_id = u.id
                order by u.id desc limit ? offset ?""", limit, Math.max(page, 0) * limit);
    }

    @PostMapping("/sellers/{telegramUserId}/suspend")
    public Map<String, Object> suspend(@PathVariable long telegramUserId, @RequestBody(required = false) StatusChange body,
                                       HttpServletRequest req) {
        var u = accounts.adminSetStatus(telegramUserId, AccountStatus.SUSPENDED, adminId(req), body == null ? null : body.reason());
        return Map.of("telegramUserId", telegramUserId, "status", u.getStatus().name());
    }

    /** SUSPENDED yoki DELETION_REQUESTED (grace davrida) -> ACTIVE. */
    @PostMapping("/sellers/{telegramUserId}/reactivate")
    public Map<String, Object> reactivate(@PathVariable long telegramUserId, @RequestBody(required = false) StatusChange body,
                                          HttpServletRequest req) {
        var u = accounts.adminSetStatus(telegramUserId, AccountStatus.ACTIVE, adminId(req), body == null ? null : body.reason());
        return Map.of("telegramUserId", telegramUserId, "status", u.getStatus().name());
    }

    @GetMapping("/support")
    public Map<String, Object> getSupport() {
        var s = support.settings();
        return mapOfNullable("enabled", s.isEnabled(), "label", s.getContactLabel(), "url", s.getContactUrl());
    }

    @PutMapping("/support")
    public Map<String, Object> putSupport(@RequestBody SupportUpdate body, HttpServletRequest req) {
        var s = support.update(body.enabled(), body.label(), body.url(), adminId(req));
        return mapOfNullable("enabled", s.isEnabled(), "label", s.getContactLabel(), "url", s.getContactUrl());
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return audit.list(page, size).map(e -> mapOfNullable(
                "id", e.getId(), "at", e.getCreatedAt().toString(), "actorType", e.getActorType(),
                "actorId", e.getActorId(), "businessId", e.getBusinessId(), "action", e.getAction(),
                "entityType", e.getEntityType(), "entityId", e.getEntityId(), "summary", e.getSummary())).getContent();
    }

    private static String adminId(HttpServletRequest req) {
        String h = req.getHeader("X-Admin-Id");
        return h == null || h.isBlank() ? "admin" : h.strip();
    }

    private static Map<String, Object> mapOfNullable(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @ExceptionHandler(Errors.NotFound.class)
    public ResponseEntity<Map<String, String>> notFound(Errors.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
    }

    @ExceptionHandler(Errors.Validation.class)
    public ResponseEntity<Map<String, String>> validation(Errors.Validation e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.code()));
    }

    /** X-Admin-Key tekshiruvi (constant-time). */
    @Component
    public static class AdminKeyFilter extends OncePerRequestFilter {
        private final byte[] key;

        public AdminKeyFilter(SellerFlowProperties props) {
            String k = props.admin() == null ? null : props.admin().apiKey();
            this.key = k == null || k.isBlank() ? null : k.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/admin/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String provided = req.getHeader("X-Admin-Key");
            if (key == null || provided == null
                    || !MessageDigest.isEqual(key, provided.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                return;
            }
            chain.doFilter(req, res);
        }
    }
}
