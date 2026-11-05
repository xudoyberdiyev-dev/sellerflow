package uz.sellerflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import uz.sellerflow.account.AccountEntities.SalesChannel;

import java.time.Instant;

/** Tenant. Bitta Telegram akkaunt = bitta biznes (SF-PRD-01, 5.1). */
@Entity
@Table(name = "businesses")
public class Business {

    public static final String DEFAULT_BUSINESS_NAME = "Mening biznesim";
    public static final String DEFAULT_DISPLAY_NAME = "Sotuvchi";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "business_name", length = 80)
    private String businessName;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_channel", nullable = false)
    private SalesChannel primaryChannel;

    @Column(nullable = false)
    private String language = "uz";

    @Column(nullable = false)
    private String currency = "UZS";

    @Column(nullable = false)
    private String timezone = "Asia/Tashkent";

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Business() {}

    public Business(Long ownerUserId, SalesChannel channel, String businessName, String displayName, String planCode) {
        this.ownerUserId = ownerUserId;
        this.primaryChannel = channel;
        this.businessName = businessName;
        this.displayName = displayName;
        this.planCode = planCode;
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getBusinessName() { return businessName; }
    public String getDisplayName() { return displayName; }
    public SalesChannel getPrimaryChannel() { return primaryChannel; }
    public String getLanguage() { return language; }
    public String getCurrency() { return currency; }
    public String getTimezone() { return timezone; }
    public String getPlanCode() { return planCode; }
    public Instant getCreatedAt() { return createdAt; }

    /** UI uchun: bo'sh bo'lsa «Mening biznesim» (DBga soxta qiymat yozilmaydi). */
    public String businessNameOrDefault() {
        return businessName == null ? DEFAULT_BUSINESS_NAME : businessName;
    }

    public String displayNameOrDefault() {
        return displayName == null ? DEFAULT_DISPLAY_NAME : displayName;
    }

    public void setBusinessName(String v) { this.businessName = v; touch(); }
    public void setDisplayName(String v) { this.displayName = v; touch(); }
    public void setPrimaryChannel(SalesChannel c) { this.primaryChannel = c; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
