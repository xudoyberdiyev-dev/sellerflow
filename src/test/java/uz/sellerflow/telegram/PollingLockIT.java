package uz.sellerflow.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.sellerflow.support.IntegrationTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** LOW: ikkinchi nusxa polling lock'ni ola olmaydi; birinchisi bo'shatgach oladi. */
class PollingLockIT extends IntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void onlyOneInstanceHoldsTheLock() {
        PollingLock a = new PollingLock(dataSource);
        PollingLock b = new PollingLock(dataSource);
        try {
            assertThat(a.ensureHeld()).isTrue();
            assertThat(a.ensureHeld()).isTrue();   // qayta chaqirish — o'sha lock
            assertThat(b.ensureHeld()).isFalse();  // ikkinchi «nusxa»
            a.close();
            assertThat(b.ensureHeld()).isTrue();
        } finally {
            a.close();
            b.close();
        }
    }
}
