package uz.sellerflow.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Tarif katalogi va entitlement (SF-PRD-02.09). Qiymatlar faqat server konfiguratsiyasidan (DB) olinadi;
 * botga tarif narxi yoki limit hard-code qilinmaydi (UX-TARIFF-008..011).
 * V1.0 Closed Beta: faqat FREE, checkout yo'q (CR-RELEASE-01).
 */
@Service
public class PlanService {

    public enum LimitType { LIMIT_APPLIED, UNLIMITED, NOT_APPLICABLE, UNKNOWN }

    @Entity
    @Table(name = "subscription_plans")
    public static class Plan {
        @Id
        private String code;
        private String name;
        private int version;
        @Column(name = "price_uzs")
        private BigDecimal priceUzs;
        private boolean published;
        private String description;

        protected Plan() {}

        public String getCode() { return code; }
        public String getName() { return name; }
        public int getVersion() { return version; }
        public BigDecimal getPriceUzs() { return priceUzs; }
        public boolean isPublished() { return published; }
        public String getDescription() { return description; }
    }

    @Embeddable
    public static class PlanFeatureId implements Serializable {
        @Column(name = "plan_code")
        private String planCode;
        private String feature;

        protected PlanFeatureId() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof PlanFeatureId other && Objects.equals(planCode, other.planCode)
                    && Objects.equals(feature, other.feature);
        }

        @Override
        public int hashCode() { return Objects.hash(planCode, feature); }
    }

    @Entity
    @Table(name = "plan_features")
    public static class PlanFeature {
        @EmbeddedId
        private PlanFeatureId id;
        @Column(name = "limit_type")
        private String limitType;
        @Column(name = "limit_value")
        private Integer limitValue;

        protected PlanFeature() {}

        public String getPlanCode() { return id.planCode; }
        public String getFeature() { return id.feature; }
        public LimitType getLimitType() {
            try {
                return LimitType.valueOf(limitType);
            } catch (RuntimeException e) {
                return LimitType.UNKNOWN; // UNKNOWN hech qachon 0 yoki UNLIMITED deb ko'rsatilmaydi
            }
        }
        public Integer getLimitValue() { return limitValue; }
    }

    public interface PlanRepository extends JpaRepository<Plan, String> {
        List<Plan> findByPublishedTrueOrderByPriceUzsAsc();
    }

    public interface PlanFeatureRepository extends JpaRepository<PlanFeature, PlanFeatureId> {
        List<PlanFeature> findByIdPlanCode(String planCode);
    }

    public record PlanView(Plan plan, List<PlanFeature> features) {}

    private final PlanRepository plans;
    private final PlanFeatureRepository features;

    public PlanService(PlanRepository plans, PlanFeatureRepository features) {
        this.plans = plans;
        this.features = features;
    }

    @Transactional(readOnly = true)
    public PlanView current(String planCode) {
        Plan p = plans.findById(planCode).orElse(null);
        return new PlanView(p, p == null ? List.of() : features.findByIdPlanCode(planCode));
    }

    @Transactional(readOnly = true)
    public List<Plan> published() {
        return plans.findByPublishedTrueOrderByPriceUzsAsc();
    }

    /**
     * Backend entitlement tekshiruvi (foydalanish hisobi yo'q funksiyalar uchun). Beta: UNLIMITED.
     * LIMIT_APPLIED uchun foydalanish soni kerak — bu yerda u yo'q, shuning uchun rad (xavfsiz default; LOW hisob).
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(String planCode, String feature) {
        return check(planCode, feature, null);
    }

    /** LIMIT_APPLIED: {@code used < limit_value} bo'lsagina ruxsat. UNLIMITED — har doim; boshqasi — rad. */
    @Transactional(readOnly = true)
    public boolean isAllowed(String planCode, String feature, long used) {
        return check(planCode, feature, used);
    }

    private boolean check(String planCode, String feature, Long used) {
        return features.findByIdPlanCode(planCode).stream()
                .filter(f -> f.getFeature().equals(feature))
                .findFirst()
                .map(f -> switch (f.getLimitType()) {
                    case UNLIMITED -> true;
                    case LIMIT_APPLIED -> used != null && f.getLimitValue() != null && used < f.getLimitValue();
                    default -> false;
                })
                .orElse(false);
    }
}
