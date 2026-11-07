package uz.sellerflow.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** LOW (testlar): production bazasini tasodifan TRUNCATE qilmaslik. */
class TestDatabaseTest {
    @Test
    void onlyDatabasesEndingWithTest_areAccepted() {
        assertThat(TestDatabase.databaseName("jdbc:postgresql://h:5432/sellerflow_test?ssl=false")).isEqualTo("sellerflow_test");
        TestDatabase.requireTestDatabase("jdbc:postgresql://localhost:5432/sellerflow_test");
        assertThatThrownBy(() -> TestDatabase.requireTestDatabase("jdbc:postgresql://db:5432/sellerflow"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("_test");
        assertThatThrownBy(() -> TestDatabase.requireTestDatabase("jdbc:postgresql://db:5432/sellerflow_test_prod"))
                .isInstanceOf(IllegalStateException.class);
    }
}
