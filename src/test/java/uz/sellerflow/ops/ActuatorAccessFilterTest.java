package uz.sellerflow.ops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorAccessFilterTest {
    @Test
    void onlyLoopbackAndPrivateAddresses() {
        for (String ip : new String[]{"127.0.0.1", "::1", "10.1.2.3", "172.18.0.5", "192.168.1.10", "fd00::1"}) {
            assertThat(ActuatorAccessFilter.isInternal(ip)).as(ip).isTrue();
        }
        for (String ip : new String[]{"8.8.8.8", "203.0.113.10", "2001:db8::1", "", "example.com", null}) {
            assertThat(ActuatorAccessFilter.isInternal(ip)).as(String.valueOf(ip)).isFalse();
        }
    }
}
