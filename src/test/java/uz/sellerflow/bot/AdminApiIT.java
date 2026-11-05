package uz.sellerflow.bot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uz.sellerflow.support.IntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiIT extends IntegrationTest {

    @Autowired TestRestTemplate http;

    private HttpEntity<String> auth(String body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Admin-Key", "test-admin-key");
        h.set("X-Admin-Id", "owner");
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    @Test
    void rejectsWithoutOrWithWrongKey() {
        assertThat(http.getForEntity("/admin/api/v1/stats", String.class).getStatusCode().value()).isEqualTo(401);
        HttpHeaders h = new HttpHeaders();
        h.set("X-Admin-Key", "wrong");
        assertThat(http.exchange("/admin/api/v1/stats", HttpMethod.GET, new HttpEntity<>(h), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void statsAndSellers_containNoCommercialData() {
        long u = onboardedUser();
        text(u, Texts.BTN_PROFIT);
        text(u, "123456");
        ResponseEntity<String> stats = http.exchange("/admin/api/v1/stats", HttpMethod.GET, auth(null), String.class);
        assertThat(stats.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> sellers = http.exchange("/admin/api/v1/sellers", HttpMethod.GET, auth(null), String.class);
        assertThat(sellers.getBody()).contains(String.valueOf(u)).contains("UZUM").doesNotContain("123456");
    }

    @Test
    void suspendAndReactivate_areAudited_andBlockBot() {
        long u = onboardedUser();
        ResponseEntity<Map> r = http.exchange("/admin/api/v1/sellers/" + u + "/suspend", HttpMethod.POST,
                auth("{\"reason\":\"test\"}"), Map.class);
        assertThat(r.getBody()).containsEntry("status", "SUSPENDED");
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("cheklangan");
        http.exchange("/admin/api/v1/sellers/" + u + "/reactivate", HttpMethod.POST, auth("{}"), Map.class);
        text(u, Texts.BTN_PRODUCTS);
        assertThat(lastText(u)).contains("Mahsulotlarim");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ADMIN_STATUS_CHANGE' and actor_id = 'owner'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void supportContact_configuredByAdmin_appearsInHelp_andInvalidUrlRejected() {
        long u = onboardedUser();
        text(u, "/help");
        assertThat(tg.last(u).buttons()).noneMatch(b -> b.url() != null);

        ResponseEntity<String> bad = http.exchange("/admin/api/v1/support", HttpMethod.PUT,
                auth("{\"enabled\":true,\"label\":\"Yordam\",\"url\":\"http://insecure\"}"), String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(400);

        http.exchange("/admin/api/v1/support", HttpMethod.PUT,
                auth("{\"enabled\":true,\"label\":\"SellerFlow yordam\",\"url\":\"@sellerflow_support\"}"), String.class);
        text(u, "/help");
        assertThat(tg.last(u).buttons()).anyMatch(b -> "https://t.me/sellerflow_support".equals(b.url()));
    }
}
