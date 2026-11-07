package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import uz.sellerflow.account.DeletionJob;
import uz.sellerflow.admin.Totp;
import uz.sellerflow.support.IntegrationTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Web Superadmin API: auth + 2FA, rollar, sotuvchilar, rozilik asosidagi kirish, audit. */
class AdminApiIT extends IntegrationTest {

    static final String OWNER = "owner@sellerflow.test";
    static final String OWNER_PASS = "correct-horse-battery-staple";

    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired DeletionJob deletionJob;
    @Autowired uz.sellerflow.common.SellerFlowProperties props;
    @Autowired uz.sellerflow.audit.AuditService auditService;
    @Autowired org.springframework.transaction.support.TransactionTemplate txTemplate;
    @Autowired uz.sellerflow.account.TermsGuard termsGuard;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired uz.sellerflow.audit.AuditRetentionJob auditRetention;
    @Autowired uz.sellerflow.admin.LoginRateLimiter loginLimiter;
    @Autowired uz.sellerflow.admin.AdminAuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void resetLoginLimiter() {
        loginLimiter.reset();
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        // JDK HttpClient: 401 javoblarida HttpURLConnection'dagi "streaming mode" muammosi yo'q
        try {
            var b = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/admin/api/v1" + path))
                    .header("Content-Type", "application/json")
                    .method(method.name(), body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                            : java.net.http.HttpRequest.BodyPublishers.ofString(body));
            if (token != null) b.header("Authorization", "Bearer " + token);
            var r = java.net.http.HttpClient.newHttpClient().send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            var h = new org.springframework.http.HttpHeaders();
            r.headers().map().forEach(h::addAll);
            return ResponseEntity.status(r.statusCode()).headers(h).body(r.body());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode json(ResponseEntity<String> r) throws Exception {
        return mapper.readTree(r.getBody());
    }

    private String code(String secret) {
        return Totp.code(secret, Instant.now().getEpochSecond() / 30);
    }

    /** Parol + TOTP (birinchi marta — 2FA ulash) orqali token olish. */
    private String login(String email, String password) throws Exception {
        // M-1: bitta 30 s qadamdagi kod faqat bir marta qabul qilinadi — testlar ketma-ket login qilishi uchun tozalaymiz
        jdbc.update("update admins set totp_last_step = null where lower(email) = lower(?)", email);
        JsonNode r = json(call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
        if ("TOTP_SETUP_REQUIRED".equals(r.path("status").asText())) {
            String secret = r.path("totpSecret").asText();
            assertThat(r.path("otpauthUri").asText()).startsWith("otpauth://totp/");
            JsonNode c = json(call(HttpMethod.POST, "/auth/totp/confirm", null,
                    "{\"setupToken\":\"" + r.path("setupToken").asText() + "\",\"code\":\"" + code(secret) + "\"}"));
            assertThat(c.path("status").asText()).isEqualTo("OK");
            return c.path("token").asText();
        }
        assertThat(r.path("status").asText()).isEqualTo("TOTP_REQUIRED");
        String secret = jdbc.queryForObject("select totp_secret from admins where lower(email) = lower(?)", String.class, email);
        JsonNode ok = json(call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"code\":\"" + code(secret) + "\"}"));
        assertThat(ok.path("status").asText()).isEqualTo("OK");
        return ok.path("token").asText();
    }

    /** Vaqtinchalik parol bilan kirib, parolni almashtiradi (M-5: aks holda boshqa API yopiq). */
    private String loginWithNewPassword(String email, String temp) throws Exception {
        String t = login(email, temp);
        assertThat(call(HttpMethod.POST, "/auth/password", t,
                "{\"currentPassword\":\"" + temp + "\",\"newPassword\":\"changed-" + temp + "\"}").getStatusCode().value())
                .isEqualTo(200);
        return t;
    }

    private String owner() throws Exception {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null where lower(email) = lower(?)", OWNER);
        return login(OWNER, OWNER_PASS);
    }

    // ------------------------------------------------------------ auth

    @Test
    void unauthenticated_andWrongCredentials_areRejected() {
        assertThat(call(HttpMethod.GET, "/stats", null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/stats", "garbage", null).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"" + OWNER + "\",\"password\":\"wrong\"}")
                .getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"nobody@x.uz\",\"password\":\"wrong\"}")
                .getStatusCode().value()).isEqualTo(401);
    }

    /** H-1 regression: xom URI va Spring MVC yo'li farqi orqali auth'ni chetlab o'tish mumkin emas. */
    @Test
    void authBypassViaPathTricks_isRejected() throws Exception {
        String[] paths = {
                "/admin/api;x=1/v1/sellers", "/admin;/api/v1/sellers", "/admin/%61pi/v1/sellers",
                "/admin/api/v1;/sellers", "/admin/api/v1/sellers;jsessionid=1", "/%61dmin/api/v1/sellers",
                "/admin/api/v1/./sellers", "/admin/x/../api/v1/sellers", "/admin/api/v1//sellers",
                "/admin/api/v1/%2e/sellers", "/admin/api/v1/stats", "/admin/api/v1/health", "/admin/api/v1/support",
                "/admin/api/v1/sellers", "/admin/api/v1/does-not-exist", "/admin/api/", "/admin/api"
        };
        for (String path : paths) {
            int status = raw("GET", path, null);
            assertThat(status).as(path).isIn(400, 401);
        }
        // Token bilan oddiy yo'l ishlaydi
        assertThat(raw("GET", "/admin/api/v1/sellers", owner())).isEqualTo(200);
    }

    private int raw(String method, String path, String token) throws Exception {
        var b = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path))
                .method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
        if (token != null) b.header("Authorization", "Bearer " + token);
        return java.net.http.HttpClient.newHttpClient()
                .send(b.build(), java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** M-1 regression: TOTP kodi (va undan eski qadam) qayta ishlatilmaydi. */
    @Test
    void totpCode_cannotBeReplayed() throws Exception {
        owner(); // 2FA ulangan bo'lsin
        jdbc.update("update admins set totp_last_step = null, failed_attempts = 0, locked_until = null where lower(email) = lower(?)", OWNER);
        String secret = jdbc.queryForObject("select totp_secret from admins where lower(email) = lower(?)", String.class, OWNER);
        long step = Instant.now().getEpochSecond() / 30;
        String body = "{\"email\":\"" + OWNER + "\",\"password\":\"" + OWNER_PASS + "\",\"code\":\"%s\"}";

        ResponseEntity<String> first = call(HttpMethod.POST, "/auth/login", null, body.formatted(Totp.code(secret, step)));
        assertThat(json(first).path("status").asText()).isEqualTo("OK");

        ResponseEntity<String> replay = call(HttpMethod.POST, "/auth/login", null, body.formatted(Totp.code(secret, step)));
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
        assertThat(json(replay).path("error").asText()).isEqualTo("INVALID_CODE");

        ResponseEntity<String> older = call(HttpMethod.POST, "/auth/login", null, body.formatted(Totp.code(secret, step - 1)));
        assertThat(older.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> next = call(HttpMethod.POST, "/auth/login", null, body.formatted(Totp.code(secret, step + 1)));
        assertThat(json(next).path("status").asText()).isEqualTo("OK");
        assertThat(jdbc.queryForObject("select totp_last_step from admins where lower(email) = lower(?)", Long.class, OWNER))
                .isEqualTo(step + 1);
    }

    /** M-4 regression: parol almashganda boshqa sessiyalar bekor; joriy parol xatosi lockout hisoblagichiga qo'shiladi. */
    @Test
    void passwordChange_revokesOtherSessions_andCountsWrongCurrentPassword() throws Exception {
        String token = owner();
        String email = "pwd" + System.nanoTime() + "@sellerflow.test";
        call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}");
        String a = login(email, "temporary-pass-123");
        String b = login(email, "temporary-pass-123");

        ResponseEntity<String> wrong = call(HttpMethod.POST, "/auth/password", a,
                "{\"currentPassword\":\"not-the-password\",\"newPassword\":\"brand-new-pass-456\"}");
        assertThat(wrong.getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select failed_attempts from admins where lower(email) = lower(?)", Integer.class, email))
                .isEqualTo(1);

        ResponseEntity<String> ok = call(HttpMethod.POST, "/auth/password", a,
                "{\"currentPassword\":\"temporary-pass-123\",\"newPassword\":\"brand-new-pass-456\"}");
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/auth/me", a, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/auth/me", b, null).getStatusCode().value()).isEqualTo(401);

        // Joriy parolni sessiya orqali taxmin qilish lockout'ga olib keladi va barcha sessiyalar bekor bo'ladi
        for (int i = 0; i < 10; i++) {
            call(HttpMethod.POST, "/auth/password", a,
                    "{\"currentPassword\":\"guess-" + i + "\",\"newPassword\":\"brand-new-pass-789\"}");
        }
        assertThat(jdbc.queryForObject("select locked_until from admins where lower(email) = lower(?)", Instant.class, email))
                .isNotNull();
        assertThat(call(HttpMethod.GET, "/auth/me", a, null).getStatusCode().value()).isEqualTo(401);
    }

    /** M-5 regression: vaqtinchalik parol bilan faqat /auth/me, /auth/password, /auth/logout ochiq. */
    @Test
    void tempPasswordSession_isRestrictedUntilChanged() throws Exception {
        String token = owner();
        String email = "temp" + System.nanoTime() + "@sellerflow.test";
        call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPERADMIN\",\"tempPassword\":\"temporary-pass-123\"}");
        String t = login(email, "temporary-pass-123");

        JsonNode me = json(call(HttpMethod.GET, "/auth/me", t, null));
        assertThat(me.path("mustChangePassword").asBoolean()).isTrue();
        for (String path : new String[]{"/stats", "/sellers", "/admins", "/audit", "/grants", "/health"}) {
            ResponseEntity<String> r = call(HttpMethod.GET, path, t, null);
            assertThat(r.getStatusCode().value()).as(path).isEqualTo(403);
            assertThat(r.getBody()).as(path).contains("PASSWORD_CHANGE_REQUIRED");
        }
        assertThat(call(HttpMethod.POST, "/auth/password", t,
                "{\"currentPassword\":\"temporary-pass-123\",\"newPassword\":\"brand-new-pass-456\"}").getStatusCode().value())
                .isEqualTo(200);
        assertThat(json(call(HttpMethod.GET, "/auth/me", t, null)).path("mustChangePassword").asBoolean()).isFalse();
        assertThat(call(HttpMethod.GET, "/stats", t, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.POST, "/auth/logout", t, null).getStatusCode().value()).isEqualTo(200);
    }

    /** M-6 regression: muvaffaqiyatsiz login va lockout manba IP bilan auditga yoziladi; parol yozilmaydi. */
    @Test
    void failedLoginsAndLockout_areAudited_withSourceIp() throws Exception {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"nobody@x.uz\",\"password\":\"secret-guess-1\"}");
        for (int i = 0; i < 10; i++) {
            loginLimiter.reset();
            call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"" + OWNER + "\",\"password\":\"secret-guess-" + i + "\"}");
        }
        var failed = jdbc.queryForList("select summary, source_ip from audit_events where action = 'ADMIN_LOGIN_FAILED' order by id");
        assertThat(failed).hasSize(11);
        assertThat(failed.get(0).get("summary").toString()).contains("unknown_email");
        assertThat(failed.get(1).get("summary").toString()).contains("reason=password").contains(OWNER);
        assertThat(failed).allSatisfy(r -> {
            assertThat(r.get("source_ip")).isNotNull();
            assertThat(r.get("summary").toString()).doesNotContain("secret-guess");
        });
        var locked = jdbc.queryForList("select source_ip from audit_events where action = 'ADMIN_LOCKED'");
        assertThat(locked).hasSize(1);
        assertThat(locked.get(0).get("source_ip")).isNotNull();
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        loginLimiter.reset();
        String token = owner();
        assertThat(json(call(HttpMethod.GET, "/audit", token, null)).path("items").get(0).has("sourceIp")).isTrue();
    }

    /** LOW (admin) regression: HSTS (HTTPS), parol 72 bayt chegarasi, paging overflow, SUPPORT grant limiti. */
    @Test
    void adminHardening_hstsPasswordLengthPagingAndGrantLimit() throws Exception {
        String token = owner();
        // HSTS faqat HTTPS (proxy X-Forwarded-Proto) da
        var https = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://localhost:" + port + "/admin/api/v1/auth/me"))
                .header("Authorization", "Bearer " + token).header("X-Forwarded-Proto", "https").build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        assertThat(https.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(v -> assertThat(v).contains("max-age="));

        // 72 baytdan uzun parol
        String longPass = "a".repeat(73);
        ResponseEntity<String> tooLong = call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"long" + System.nanoTime() + "@sellerflow.test\",\"role\":\"SUPPORT\",\"tempPassword\":\"" + longPass + "\"}");
        assertThat(tooLong.getStatusCode().value()).isEqualTo(400);
        assertThat(tooLong.getBody()).contains("PASSWORD_TOO_LONG");

        // paging overflow
        assertThat(call(HttpMethod.GET, "/sellers?page=2147483647&size=200", token, null).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.GET, "/sellers?page=-1", token, null).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.GET, "/audit?page=2147483647", token, null).getStatusCode().value()).isEqualTo(400);

        // SUPPORT: bitta sotuvchiga 24 soatda 3 ta so'rov
        String email = "lim" + System.nanoTime() + "@sellerflow.test";
        call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}");
        String support = loginWithNewPassword(email, "temporary-pass-123");
        long u = onboardedUser();
        String req = "{\"telegramUserId\":" + u + ",\"purpose\":\"Hisob natijasini tekshirish uchun\",\"minutes\":30}";
        for (int i = 0; i < 3; i++) {
            assertThat(call(HttpMethod.POST, "/grants", support, req).getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<String> fourth = call(HttpMethod.POST, "/grants", support, req);
        assertThat(fourth.getStatusCode().value()).isEqualTo(400);
        assertThat(fourth.getBody()).contains("GRANT_LIMIT");
        // SUPERADMIN uchun limit yo'q
        assertThat(call(HttpMethod.POST, "/grants", token, req).getStatusCode().value()).isEqualTo(200);
    }

    /** M-13: Prometheus metrikalari lokal tarmoqdan ochiq; tashqi IP (X-Forwarded-For) uchun 404. */
    @Test
    void prometheusMetrics_exposedLocally_hiddenFromInternet() throws Exception {
        long u = onboardedUser();
        assertThat(u).isPositive();
        var client = java.net.http.HttpClient.newHttpClient();
        var local = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/actuator/prometheus")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(local.statusCode()).isEqualTo(200);
        assertThat(local.body()).contains("sellerflow_updates_total").contains("jvm_memory_used_bytes");
        var external = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/actuator/prometheus"))
                .header("X-Forwarded-For", "203.0.113.10").build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(external.statusCode()).isEqualTo(404);
    }

    /** LOW (UI): index.html keshlanmaydi, hash'li assetlar uzoq keshlanadi. */
    @Test
    void adminSpa_cacheHeaders() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        var index = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/admin/")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.headers().firstValue("Cache-Control")).hasValue("no-cache");
        var asset = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/admin/assets/app-abc123.js")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("immutable"));
    }

    /** M-12: Docker healthcheck uchun /actuator/health/container (db + bot). */
    @Test
    void containerHealthGroup_isExposed() throws Exception {
        var r = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/actuator/health/container")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"status\":\"UP\"");
    }

    /** M-21: adminlarni boshqarish — create validatsiyasi, disable/enable, reset-2fa, o'zini o'zgartira olmaslik. */
    @Test
    void adminManagement_createDisableEnableReset2fa() throws Exception {
        String token = owner();
        long ownerId = jdbc.queryForObject("select id from admins where lower(email) = lower(?)", Long.class, OWNER);
        String email = "mgmt" + System.nanoTime() + "@sellerflow.test";

        assertThat(call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"not-an-email\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}").getBody()).contains("EMAIL_INVALID");
        assertThat(call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"short\"}").getBody()).contains("PASSWORD_TOO_SHORT");
        JsonNode created = json(call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}"));
        long id = created.path("id").asLong();
        assertThat(call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email.toUpperCase() + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}").getBody()).contains("EMAIL_TAKEN");
        assertThat(json(call(HttpMethod.GET, "/admins", token, null)).toString()).contains(email);

        String support = loginWithNewPassword(email, "temporary-pass-123");
        assertThat(call(HttpMethod.GET, "/admins", support, null).getStatusCode().value()).isEqualTo(403); // SUPPORT boshqara olmaydi
        assertThat(call(HttpMethod.POST, "/admins/" + ownerId + "/disable", support, null).getStatusCode().value()).isEqualTo(403);

        // o'zini o'chira olmaydi
        assertThat(call(HttpMethod.POST, "/admins/" + ownerId + "/disable", token, null).getBody()).contains("CANNOT_CHANGE_SELF");

        // disable → sessiyalar bekor, login yo'q
        assertThat(call(HttpMethod.POST, "/admins/" + id + "/disable", token, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/auth/me", support, null).getStatusCode().value()).isEqualTo(401);
        loginLimiter.reset();
        assertThat(call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"changed-temporary-pass-123\"}").getStatusCode().value()).isEqualTo(401);

        // enable → yana kira oladi
        assertThat(call(HttpMethod.POST, "/admins/" + id + "/enable", token, null).getStatusCode().value()).isEqualTo(200);
        loginLimiter.reset();
        String again = login(email, "changed-temporary-pass-123");
        assertThat(call(HttpMethod.GET, "/auth/me", again, null).getStatusCode().value()).isEqualTo(200);

        // reset-2fa → sessiyalar bekor, keyingi login 2FA ni qayta ulaydi
        assertThat(call(HttpMethod.POST, "/admins/" + id + "/reset-2fa", token, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/auth/me", again, null).getStatusCode().value()).isEqualTo(401);
        JsonNode r = json(call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"changed-temporary-pass-123\"}"));
        assertThat(r.path("status").asText()).isEqualTo("TOTP_SETUP_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action in ('ADMIN_CREATED','ADMIN_STATUS_DISABLED','ADMIN_STATUS_ACTIVE','ADMIN_2FA_RESET')", Long.class))
                .isEqualTo(4);
    }

    /** D-5: sotuvchi kartasi — metadata va sonlar, tijoriy qiymatlarsiz; SUPPORT o'qiy oladi; 404. */
    @Test
    void sellerCard_metadataOnly() throws Exception {
        String token = owner();
        long u = onboardedUser();
        call(HttpMethod.POST, "/sellers/" + u + "/suspend", token, "{\"reason\":\"Spam faoliyati tekshiruvi\"}");
        JsonNode r = json(call(HttpMethod.GET, "/sellers/" + u, token, null));
        assertThat(r.path("telegramUserId").asLong()).isEqualTo(u);
        assertThat(r.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(r.path("primaryChannel").asText()).isNotBlank();
        assertThat(r.path("terms").path("version").asText()).isNotBlank();
        assertThat(r.path("productCount").asLong()).isZero();
        assertThat(r.path("savedCalculationCount").asLong()).isZero();
        assertThat(r.path("lastStatusChange").asText()).contains("Spam faoliyati");
        assertThat(r.has("userId")).isFalse();
        assertThat(r.toString()).doesNotContain("unitCost").doesNotContain("profit");
        assertThat(call(HttpMethod.GET, "/sellers/999999999999", token, null).getStatusCode().value()).isEqualTo(404);

        String email = "card" + System.nanoTime() + "@sellerflow.test";
        call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}");
        String support = loginWithNewPassword(email, "temporary-pass-123");
        assertThat(call(HttpMethod.GET, "/sellers/" + u, support, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/sellers/" + u, null, null).getStatusCode().value()).isEqualTo(401);
    }

    /** D-5: /auth/me expiresAt, sessiyalar ro'yxati (xeshsiz) va boshqalarini bekor qilish. */
    @Test
    void sessions_listAndRevokeOthers() throws Exception {
        String a = owner();
        String b = owner();
        assertThat(json(call(HttpMethod.GET, "/auth/me", a, null)).path("expiresAt").asText()).isNotBlank();
        JsonNode list = json(call(HttpMethod.GET, "/auth/sessions", a, null));
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
        long current = 0;
        for (JsonNode n : list) if (n.path("current").asBoolean()) current++;
        assertThat(current).isEqualTo(1);
        assertThat(list.toString()).doesNotContain("token").doesNotContain("hash");
        JsonNode r = json(call(HttpMethod.POST, "/auth/sessions/revoke-others", a, null));
        assertThat(r.path("revoked").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(call(HttpMethod.GET, "/auth/me", b, null).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/auth/me", a, null).getStatusCode().value()).isEqualTo(200);
        assertThat(json(call(HttpMethod.GET, "/auth/sessions", a, null)).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ADMIN_SESSIONS_REVOKED'", Long.class)).isEqualTo(1);
    }

    @Test
    void loginWith2fa_meAndLogout() throws Exception {
        String token = owner();
        JsonNode me = json(call(HttpMethod.GET, "/auth/me", token, null));
        assertThat(me.path("role").asText()).isEqualTo("SUPERADMIN");
        call(HttpMethod.POST, "/auth/logout", token, null);
        assertThat(call(HttpMethod.GET, "/auth/me", token, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void accountLockout_afterTenFailures_responseDoesNotRevealPassword() {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        for (int i = 0; i < 10; i++) {
            loginLimiter.reset(); // turli IP'lardan kelgandek (IP limiti alohida testda)
            call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"" + OWNER + "\",\"password\":\"wrong-" + i + "\"}");
        }
        loginLimiter.reset();
        ResponseEntity<String> right = call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + OWNER + "\",\"password\":\"" + OWNER_PASS + "\"}");
        loginLimiter.reset();
        ResponseEntity<String> wrong = call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + OWNER + "\",\"password\":\"still-wrong\"}");
        assertThat(right.getBody()).contains("LOCKED");
        // M-2: bloklangan akkauntda javob parol to'g'ri/noto'g'riligiga bog'liq emas
        assertThat(wrong.getStatusCode()).isEqualTo(right.getStatusCode());
        assertThat(wrong.getBody()).isEqualTo(right.getBody());
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
    }

    /** M-2 regression: bitta IP'dan 5 ta xatodan keyin 429, akkaunt esa bloklanmaydi (lockout DoS yo'q). */
    @Test
    void ipRateLimit_blocksBeforeAccountLockout() throws Exception {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        for (int i = 0; i < 5; i++) {
            assertThat(call(HttpMethod.POST, "/auth/login", null,
                    "{\"email\":\"" + OWNER + "\",\"password\":\"wrong-" + i + "\"}").getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<String> blocked = call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + OWNER + "\",\"password\":\"" + OWNER_PASS + "\"}");
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getBody()).contains("TOO_MANY_ATTEMPTS");
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(jdbc.queryForObject("select locked_until from admins where lower(email) = lower(?)", Instant.class, OWNER)).isNull();
        loginLimiter.reset();
        assertThat(owner()).isNotBlank();
    }

    /** M-3 regression: parallel xato urinishlar hisoblagichda yo'qolmaydi. */
    @Test
    void failedAttemptCounter_isAtomicUnderConcurrency() throws Exception {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        int n = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        var start = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < n; i++) {
            int k = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    authService.login(OWNER, "wrong-parallel-" + k, null);
                } catch (uz.sellerflow.common.Errors.Forbidden expected) {
                    // kutilgan
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(60, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(jdbc.queryForObject("select failed_attempts from admins where lower(email) = lower(?)", Integer.class, OWNER))
                .isEqualTo(n);
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
    }

    @Test
    void supportRole_cannotSuspendOrManageAdmins_butCanListSellers() throws Exception {
        String token = owner();
        String email = "support" + System.nanoTime() + "@sellerflow.test";
        ResponseEntity<String> created = call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        String support = loginWithNewPassword(email, "temporary-pass-123");
        long u = onboardedUser();
        assertThat(call(HttpMethod.GET, "/sellers", support, null).getBody()).contains(String.valueOf(u));
        assertThat(call(HttpMethod.POST, "/sellers/" + u + "/suspend", support, "{}").getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/admins", support, null).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/audit", support, null).getStatusCode().value()).isEqualTo(403);
    }

    // ------------------------------------------------------------ sellers

    @Test
    void statsAndSellers_containNoCommercialData() throws Exception {
        String token = owner();
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "123456");
        assertThat(call(HttpMethod.GET, "/stats", token, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/health", token, null).getBody()).contains("\"database\":\"UP\"");
        String sellers = call(HttpMethod.GET, "/sellers?telegramUserId=" + u, token, null).getBody();
        assertThat(sellers).contains(String.valueOf(u)).contains("UZUM").doesNotContain("123456");
    }

    @Test
    void suspendAndReactivate_areAudited_andBlockBot() throws Exception {
        String token = owner();
        long u = onboardedUser();
        assertThat(call(HttpMethod.POST, "/sellers/" + u + "/suspend", token, "{\"reason\":\"test\"}").getBody())
                .contains("SUSPENDED");
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("cheklangan");
        call(HttpMethod.POST, "/sellers/" + u + "/reactivate", token, "{}");
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("Mahsulotlarim");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ADMIN_STATUS_CHANGE' and actor_id = ?",
                Long.class, OWNER)).isEqualTo(2);
    }

    @Test
    void supportContact_configuredByAdmin_appearsInHelp_andInvalidUrlRejected() throws Exception {
        String token = owner();
        long u = onboardedUser();
        text(u, "/help");
        assertThat(tg.last(u).buttons()).noneMatch(b -> b.url() != null);
        assertThat(call(HttpMethod.PUT, "/support", token, "{\"enabled\":true,\"label\":\"Yordam\",\"url\":\"http://insecure\"}")
                .getStatusCode().value()).isEqualTo(400);
        call(HttpMethod.PUT, "/support", token, "{\"enabled\":true,\"label\":\"SellerFlow yordam\",\"url\":\"@sellerflow_support\"}");
        text(u, "/help");
        assertThat(tg.last(u).buttons()).anyMatch(b -> "https://t.me/sellerflow_support".equals(b.url()));
    }

    // ------------------------------------------------------------ consent-based access

    @Test
    void accessGrant_requiresSellerApproval_isReadOnlyAudited_andRevocable() throws Exception {
        String token = owner();
        long u = onboardedUser();
        text(u, Texts.BTN_PRODUCTS);
        click(u, "Yangi mahsulot");
        text(u, "Maxfiy mahsulot");
        text(u, "777000");
        click(u, "O‘tkazib yuborish");
        click(u, "O‘tkazib yuborish");
        click(u, "Saqlash");

        assertThat(call(HttpMethod.POST, "/grants", token,
                "{\"telegramUserId\":" + u + ",\"purpose\":\"x\",\"minutes\":30}").getStatusCode().value()).isEqualTo(400);
        JsonNode g = json(call(HttpMethod.POST, "/grants", token,
                "{\"telegramUserId\":" + u + ",\"purpose\":\"Sotuvchi hisob natijasini tushunmayapti\",\"minutes\":45}"));
        long gid = g.path("id").asLong();
        assertThat(g.path("status").asText()).isEqualTo("REQUESTED");
        assertThat(lastText(u)).contains("Qo‘llab-quvvatlash so‘rovi").contains("45 daqiqaga");

        // tasdiqdan oldin ma'lumot yo'q
        assertThat(call(HttpMethod.GET, "/grants/" + gid + "/data", token, null).getStatusCode().value()).isEqualTo(403);

        click(u, "Ruxsat berish");
        assertThat(lastText(u)).contains("Ruxsat berildi");
        ResponseEntity<String> data = call(HttpMethod.GET, "/grants/" + gid + "/data", token, null);
        assertThat(data.getStatusCode().value()).isEqualTo(200);
        assertThat(data.getBody()).contains("Maxfiy mahsulot").contains("777000");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCESS_DATA_VIEWED'", Long.class)).isEqualTo(1);

        // boshqa admin ko'ra olmaydi (ruxsat o'tkazilmaydi)
        String email = "s" + System.nanoTime() + "@sellerflow.test";
        call(HttpMethod.POST, "/admins", token, "{\"email\":\"" + email + "\",\"role\":\"SUPERADMIN\",\"tempPassword\":\"temporary-pass-123\"}");
        String other = loginWithNewPassword(email, "temporary-pass-123");
        assertThat(call(HttpMethod.GET, "/grants/" + gid + "/data", other, null).getStatusCode().value()).isEqualTo(403);

        // sotuvchi bekor qiladi
        click(u, "Ruxsatni bekor qilish");
        assertThat(call(HttpMethod.GET, "/grants/" + gid + "/data", token, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void accessGrant_expiresAutomatically_andDeclineWorks() throws Exception {
        String token = owner();
        long u = onboardedUser();
        long gid = json(call(HttpMethod.POST, "/grants", token,
                "{\"telegramUserId\":" + u + ",\"purpose\":\"Hisob natijasini tekshirish uchun\",\"minutes\":30}")).path("id").asLong();
        click(u, "Ruxsat berish");
        jdbc.update("update access_grants set expires_at = now() - interval '1 second' where id = ?", gid);
        assertThat(call(HttpMethod.GET, "/grants/" + gid + "/data", token, null).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/grants", token, null).getBody()).contains("EXPIRED");

        long gid2 = json(call(HttpMethod.POST, "/grants", token,
                "{\"telegramUserId\":" + u + ",\"purpose\":\"Yana bir marta tekshirish uchun\",\"minutes\":30}")).path("id").asLong();
        click(u, "Rad etish");
        assertThat(call(HttpMethod.GET, "/grants/" + gid2 + "/data", token, null).getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select status from access_grants where id = ?", String.class, gid2)).isEqualTo("DECLINED");
    }

    // ------------------------------------------------------------ account policies

    @Test
    void reconsent_requiredWhenNoConsentForCurrentVersion() {
        long u = onboardedUser();
        jdbc.update("delete from consents");
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("shartlari yangilandi");
        click(u, "Rozi emasman");
        assertThat(lastText(u)).contains("rozilik berilmaguncha");
        click(u, "Shartlarni qayta");
        click(u, "Roziman");
        assertThat(lastText(u)).contains("Rozilik qabul qilindi");
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("Mahsulotlarim");
        assertThat(jdbc.queryForObject("select count(*) from consents", Long.class)).isEqualTo(1);
    }

    @Test
    void deletionJob_closesAfterGrace_clearsProfile_keepsCommercialDataByDefault() {
        long u = onboardedUser();
        text(u, Texts.BTN_SETTINGS);
        click(u, "Biznes nomi");
        text(u, "Mening do‘konim");
        click(u, "Akkauntni o‘chirish");
        click(u, "Davom etish");
        click(u, "Ha, so‘rov yuborilsin");
        assertThat(deletionJob.run()).isZero(); // kutish davri hali tugamagan
        jdbc.update("update users set deletion_requested_at = now() - interval '31 days'");
        assertThat(deletionJob.run()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select business_name from businesses", String.class)).isNull();
        text(u, "/start");
        assertThat(lastText(u)).contains("yopilgan");
    }

    /** M-23 + M-25: o'chirish matni haqiqatga mos; foydalanuvchi so'rovni botning o'zida bekor qiladi. */
    @Test
    void deletionRequest_textIsTruthful_andUserCanCancelInBot() {
        long u = onboardedUser();
        text(u, Texts.BTN_SETTINGS);
        click(u, "Akkauntni o‘chirish");
        assertThat(lastText(u)).contains("saqlanib qoladi").contains("botning o‘zida").doesNotContain("qonunchilikka muvofiq");
        click(u, "Davom etish");
        click(u, "Ha, so‘rov yuborilsin");
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("DELETION_REQUESTED");
        text(u, "/menu");
        click(u, "O‘chirish so‘rovini bekor qilish");
        assertThat(lastText(u)).contains("bekor qilindi");
        assertThat(jdbc.queryForMap("select status, deletion_requested_at from users where telegram_user_id = ?", u))
                .containsEntry("status", "ACTIVE").containsEntry("deletion_requested_at", null);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'DELETION_CANCELLED'", Long.class)).isEqualTo(1);
        // muddati o'tgan so'rov endi yopilmaydi
        jdbc.update("update users set deletion_requested_at = now() - interval '31 days' where telegram_user_id = ?", u);
        assertThat(deletionJob.run()).isZero();
    }

    /** M-24: CLOSED foydalanuvchi yangi akkaunt ochadi; eski ma'lumot unga o'tmaydi. */
    @Test
    void closedAccount_canStartFresh_withoutOldData() {
        long u = onboardedUser();
        long oldUserId = jdbc.queryForObject("select id from users where telegram_user_id = ?", Long.class, u);
        jdbc.update("update users set status = 'CLOSED' where id = ?", oldUserId);
        text(u, "/start");
        assertThat(lastText(u)).contains("yopilgan").contains("o‘tkazilmaydi");
        click(u, "Yangi akkaunt ochish");
        assertThat(jdbc.queryForObject("select telegram_user_id from users where id = ?", Long.class, oldUserId)).isNegative();
        var fresh = jdbc.queryForMap("select id, status from users where telegram_user_id = ?", u);
        assertThat(fresh.get("status")).isEqualTo("ONBOARDING");
        assertThat(((Number) fresh.get("id")).longValue()).isNotEqualTo(oldUserId);
        assertThat(lastText(u)).isNotBlank();
        // onboarding yangidan o'tadi va yangi biznes yaratiladi
        click(u, "Boshlash");
        click(u, "Roziman");
        click(u, "Uzum Market");
        click(u, "O‘tkazib yuborish");
        click(u, "O‘tkazib yuborish");
        click(u, "Faollashtirish");
        assertThat(lastText(u)).contains("faollashtirildi");
        assertThat(jdbc.queryForObject("select count(*) from businesses", Long.class)).isEqualTo(2);
    }

    /** LOW (DB): audit saqlash muddati; @Version lost update'ni to'xtatadi. */
    @Test
    void auditRetention_deletesOldEvents_andVersionColumnsBump() {
        long u = onboardedUser();
        jdbc.update("update audit_events set created_at = now() - interval '400 days' where action = 'USER_REGISTERED'");
        long before = jdbc.queryForObject("select count(*) from audit_events", Long.class);
        assertThat(auditRetention.run()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isEqualTo(before - 1);
        long v0 = jdbc.queryForObject("select version from users where telegram_user_id = ?", Long.class, u);
        text(u, Texts.BTN_SETTINGS);
        click(u, "Akkauntni o‘chirish");
        click(u, "Davom etish");
        click(u, "Ha, so‘rov yuborilsin");
        assertThat(jdbc.queryForObject("select version from users where telegram_user_id = ?", Long.class, u)).isGreaterThan(v0);
    }

    /** LOW (maxfiylik): shartlar matni o'zgarib versiya o'zgarmasa — xato. */
    @Test
    void termsVersion_isBoundToTextHash() {
        String v = "test-" + System.nanoTime();
        termsGuard.verify(v, "Shartlar matni A");
        termsGuard.verify(v, "Shartlar matni A"); // bir xil — OK
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> termsGuard.verify(v, "Shartlar matni B"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TERMS_VERSION");
        termsGuard.verify(v + "-2", "Shartlar matni B"); // yangi versiya — OK
    }

    /** LOW (maxfiylik): bloklashda faol grantlar DB'da bekor; USER audit yozuvida actor_id bor. */
    @Test
    void suspend_revokesGrantsInDb_andUserAuditHasActorId() throws Exception {
        String token = owner();
        long u = onboardedUser();
        long gid = json(call(HttpMethod.POST, "/grants", token,
                "{\"telegramUserId\":" + u + ",\"purpose\":\"Hisob natijasini tekshirish uchun\",\"minutes\":30}")).path("id").asLong();
        click(u, "Ruxsat berish");
        assertThat(jdbc.queryForObject("select status from access_grants where id = ?", String.class, gid)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select actor_id from audit_events where action = 'ACCESS_APPROVED'", String.class)).isNotNull();
        JsonNode list = json(call(HttpMethod.GET, "/grants", token, null));
        assertThat(list.get(0).path("telegramUserId").asLong()).isEqualTo(u); // LOW (DB): batch so'rov
        assertThat(list.get(0).path("adminEmail").asText()).isEqualToIgnoringCase(OWNER);
        call(HttpMethod.POST, "/sellers/" + u + "/suspend", token, "{\"reason\":\"test\"}");
        assertThat(jdbc.queryForObject("select status from access_grants where id = ?", String.class, gid)).isEqualTo("REVOKED");
    }

    /** LOW (maxfiylik): purge keyin yoqilsa, oldin yopilgan akkauntlar ham tozalanadi. */
    @Test
    void enablingPurgeLater_cleansPreviouslyClosedAccounts() {
        long u = onboardedUser();
        long bid = jdbc.queryForObject("select b.id from businesses b join users x on x.id = b.owner_user_id where x.telegram_user_id = ?", Long.class, u);
        jdbc.update("update users set status = 'CLOSED' where telegram_user_id = ?", u);
        var base = props;
        var purgeProps = new uz.sellerflow.common.SellerFlowProperties(base.telegram(), base.admin(), base.terms(), base.draft(),
                base.support(), new uz.sellerflow.common.SellerFlowProperties.Deletion(30, true), base.rateLimit());
        txTemplate.executeWithoutResult(st -> new DeletionJob(jdbc, auditService, purgeProps, txManager).run());
        assertThat(jdbc.queryForObject("select count(*) from users where telegram_user_id = ?", Long.class, u)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCOUNT_PURGED'", Long.class)).isEqualTo(1);
    }

    /** M-23: purge yoqilganda Telegram ID ham psevdonimga almashtiriladi. */
    @Test
    void deletionJob_withPurge_pseudonymizesTelegramId() {
        long u = onboardedUser();
        jdbc.update("update users set status = 'DELETION_REQUESTED', deletion_requested_at = now() - interval '31 days' where telegram_user_id = ?", u);
        var base = props;
        var purgeProps = new uz.sellerflow.common.SellerFlowProperties(base.telegram(), base.admin(), base.terms(), base.draft(),
                base.support(), new uz.sellerflow.common.SellerFlowProperties.Deletion(30, true), base.rateLimit());
        var job = new DeletionJob(jdbc, auditService, purgeProps, txManager);
        assertThat((int) txTemplate.execute(st -> job.run())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from users where telegram_user_id = ?", Long.class, u)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users where status = 'CLOSED' and telegram_user_id < 0", Long.class)).isEqualTo(1);
        // keyingi /start — yangi akkaunt (onboarding)
        text(u, "/start");
        assertThat(jdbc.queryForObject("select status from users where telegram_user_id = ?", String.class, u)).isEqualTo("ONBOARDING");
    }
}
