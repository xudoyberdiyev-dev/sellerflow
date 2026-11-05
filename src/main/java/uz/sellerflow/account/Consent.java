package uz.sellerflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt = Instant.now();

    protected Consent() {}

    public Consent(Long userId, String termsVersion) {
        this.userId = userId;
        this.termsVersion = termsVersion;
    }

    public Long getUserId() { return userId; }
    public String getTermsVersion() { return termsVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
}
