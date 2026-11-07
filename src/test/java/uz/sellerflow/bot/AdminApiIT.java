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
            return ResponseEntity.status(r.statusCode()).body(r.body());
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

    @Test
    void loginWith2fa_meAndLogout() throws Exception {
        String token = owner();
        JsonNode me = json(call(HttpMethod.GET, "/auth/me", token, null));
        assertThat(me.path("role").asText()).isEqualTo("SUPERADMIN");
        call(HttpMethod.POST, "/auth/logout", token, null);
        assertThat(call(HttpMethod.GET, "/auth/me", token, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void lockoutAfterFiveFailures() {
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/auth/login", null, "{\"email\":\"" + OWNER + "\",\"password\":\"wrong-" + i + "\"}");
        }
        ResponseEntity<String> r = call(HttpMethod.POST, "/auth/login", null,
                "{\"email\":\"" + OWNER + "\",\"password\":\"" + OWNER_PASS + "\"}");
        assertThat(r.getBody()).contains("LOCKED");
        jdbc.update("update admins set failed_attempts = 0, locked_until = null");
    }

    @Test
    void supportRole_cannotSuspendOrManageAdmins_butCanListSellers() throws Exception {
        String token = owner();
        String email = "support" + System.nanoTime() + "@sellerflow.test";
        ResponseEntity<String> created = call(HttpMethod.POST, "/admins", token,
                "{\"email\":\"" + email + "\",\"role\":\"SUPPORT\",\"tempPassword\":\"temporary-pass-123\"}");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        String support = login(email, "temporary-pass-123");
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
        String other = login(email, "temporary-pass-123");
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
}
