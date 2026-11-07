package uz.sellerflow.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFirewallTest {

    @Test
    void rejectsAmbiguousPaths() {
        for (String u : new String[]{"/admin/api;x=1/v1", "/admin;/api", "/a/%2F/b", "/a/%2e/b", "/a\\\\b", "/a//b",
                "/a/./b", "/a/../b", "/a/..", "/a/%25", "/a/%3B", "/a/%00", "relative", "", "/a b"}) {
            assertThat(RequestFirewall.isRejected(u)).as(u).isTrue();
        }
    }

    @Test
    void allowsNormalPaths() {
        for (String u : new String[]{"/", "/admin/", "/admin/api/v1/sellers", "/admin/assets/index-AbC12.js",
                "/actuator/health", "/admin/%61pi/v1/sellers"}) {
            assertThat(RequestFirewall.isRejected(u)).as(u).isFalse();
        }
    }
}
