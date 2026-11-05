package uz.sellerflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.sellerflow.account.AccountEntities.AccountStatus;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserAccount() {}

    public UserAccount(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
        this.status = AccountStatus.ONBOARDING;
    }

    public Long getId() { return id; }
    public Long getTelegramUserId() { return telegramUserId; }
    public AccountStatus getStatus() { return status; }
    public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(AccountStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void markDeletionRequested(Instant at) {
        this.deletionRequestedAt = at;
        setStatus(AccountStatus.DELETION_REQUESTED);
    }
}
