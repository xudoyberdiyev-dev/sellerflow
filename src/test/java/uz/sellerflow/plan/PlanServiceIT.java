package uz.sellerflow.plan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.sellerflow.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** LOW (hisob): LIMIT_APPLIED endi «cheksiz» deb talqin qilinmaydi. */
class PlanServiceIT extends IntegrationTest {

    @Autowired PlanService plans;

    @Test
    void limitApplied_respectsUsage_andDeniesWithoutUsage() {
        jdbc.update("insert into subscription_plans(code, name) values ('TLIM', 'Test') on conflict do nothing");
        jdbc.update("insert into plan_features(plan_code, feature, limit_type, limit_value) values ('TLIM', 'PRODUCT_CATALOG', 'LIMIT_APPLIED', 3) on conflict do nothing");
        jdbc.update("insert into plan_features(plan_code, feature, limit_type, limit_value) values ('TLIM', 'PROFIT_CALCULATION', 'LIMIT_APPLIED', 10) on conflict do nothing");
        assertThat(plans.isAllowed("TLIM", "PRODUCT_CATALOG", 0)).isTrue();
        assertThat(plans.isAllowed("TLIM", "PRODUCT_CATALOG", 2)).isTrue();
        assertThat(plans.isAllowed("TLIM", "PRODUCT_CATALOG", 3)).isFalse();
        assertThat(plans.isAllowed("TLIM", "PROFIT_CALCULATION")).isFalse(); // foydalanish hisobi yo'q — xavfsiz rad
        assertThat(plans.isAllowed("TLIM", "UNKNOWN_FEATURE", 0)).isFalse();
        assertThat(plans.isAllowed("FREE", "PROFIT_CALCULATION")).isTrue();
        assertThat(plans.isAllowed("FREE", "PRODUCT_CATALOG", 1_000_000)).isTrue();
    }
}
