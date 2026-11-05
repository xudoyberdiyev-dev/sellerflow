package uz.sellerflow.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.sellerflow.account.AccountEntities.AccountStatus;

import java.util.List;
import java.util.Optional;

public final class AccountRepositories {
    private AccountRepositories() {}

    public interface UserRepository extends JpaRepository<UserAccount, Long> {
        Optional<UserAccount> findByTelegramUserId(Long telegramUserId);

        long countByStatus(AccountStatus status);
    }

    public interface BusinessRepository extends JpaRepository<Business, Long> {
        Optional<Business> findByOwnerUserId(Long ownerUserId);
    }

    public interface ConsentRepository extends JpaRepository<Consent, Long> {
        Optional<Consent> findByUserIdAndTermsVersion(Long userId, String termsVersion);

        @Query("select c from Consent c where c.userId = ?1 order by c.acceptedAt desc")
        List<Consent> findAllByUser(Long userId);
    }
}
