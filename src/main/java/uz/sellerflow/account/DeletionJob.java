package uz.sellerflow.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.common.SellerFlowProperties;

import java.util.List;

/**
 * O'chirish so'rovidan keyingi kutish davri (30 kun) tugagach: DELETION_REQUESTED -> CLOSED (SF-PRD-01, 5.3/5.4).
 * Har doim: faol qoralama va shaxsiy profil maydonlari (biznes nomi, ism) tozalanadi.
 * purge-data=true bo'lsa (yurist tasdiqlagan retention siyosatidan keyin): mahsulotlar va tarix ham o'chiriladi.
 * Rozilik yozuvlari va audit jurnali dalil sifatida saqlanadi.
 */
@Component
public class DeletionJob {

    private static final Logger log = LoggerFactory.getLogger(DeletionJob.class);

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SellerFlowProperties props;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final AccountPurger purger;

    public DeletionJob(JdbcTemplate jdbc, AuditService audit, SellerFlowProperties props,
                       org.springframework.transaction.PlatformTransactionManager tm, AccountPurger purger) {
        this.purger = purger;
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(tm);
        // LOW (DB): har akkaunt alohida tranzaksiyada — bittasi xato bersa qolganlari qaytarilmaydi
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Tashkent")
    public int run() {
        int grace = props.deletion() == null ? 30 : props.deletion().graceDays();
        boolean purge = props.deletion() != null && props.deletion().purgeData();
        List<Long> due = jdbc.queryForList("""
                select id from users where status = 'DELETION_REQUESTED'
                  and deletion_requested_at < now() - make_interval(days => ?)""", Long.class, grace);
        int closed = 0;
        for (Long userId : due) {
            try {
                Boolean done = tx.execute(st -> closeOne(userId, grace, purge));
                if (Boolean.TRUE.equals(done)) closed++;
            } catch (RuntimeException e) {
                log.error("Deletion job failed for user {} — will retry next run", userId, e);
            }
        }
        if (closed > 0) log.info("Closed {} accounts after deletion grace period", closed);
        if (purge) purgePreviouslyClosed();
        cleanupStaleOnboarding();
        return closed;
    }

    private boolean closeOne(Long userId, int grace, boolean purge) {
        // LOW (TOCTOU): holat SELECT'dan keyin o'zgargan bo'lishi mumkin (foydalanuvchi bekor qildi) — qatorni qulflab qayta tekshiramiz
        String st = jdbc.query("select status from users where id = ? for update", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (!"DELETION_REQUESTED".equals(st)) return false;
        Long businessId = jdbc.query("select id from businesses where owner_user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
        if (purge) {
            purger.purge(userId, "deletion-job", "grace " + grace + " days; commercial data purged, telegram id pseudonymized");
            return true;
        }
        purger.clearActivity(userId);
        if (businessId != null) {
            jdbc.update("update businesses set business_name = null, display_name = null, updated_at = now() where id = ?", businessId);
            jdbc.update("update access_grants set status = 'REVOKED', revoked_at = now(), revoked_by = 'SYSTEM', version = version + 1 "
                    + "where business_id = ? and status in ('REQUESTED','APPROVED')", businessId);
        }
        jdbc.update("update users set status = 'CLOSED', status_before_deletion = null, updated_at = now(), version = version + 1 where id = ?", userId);
        audit.record(Actor.SYSTEM, "deletion-job", businessId, "ACCOUNT_CLOSED", "USER", userId,
                "grace " + grace + " days; commercial data retained (pending legal policy)");
        return true;
    }

    /**
     * LOW (maxfiylik) + N-9: DELETION_PURGE_DATA keyinroq yoqilsa — avval (purge'siz) yopilgan barcha akkauntlar,
     * jumladan Telegram ID'dan ajratilganlar ham ({@code purged_at is null} bo'yicha), tozalanadi.
     */
    private void purgePreviouslyClosed() {
        List<Long> old = jdbc.queryForList("select id from users where status = 'CLOSED' and purged_at is null", Long.class);
        int n = 0;
        for (Long userId : old) {
            try {
                tx.executeWithoutResult(st -> purger.purge(userId, "deletion-job",
                        "previously closed account purged after DELETION_PURGE_DATA was enabled"));
                n++;
            } catch (RuntimeException e) {
                log.error("Purge failed for closed user {} — will retry next run", userId, e);
            }
        }
        if (n > 0) log.info("Purged {} previously closed accounts", n);
    }

    /**
     * N-11: /start (yoki /help) bosib, ro'yxatdan o'tishni tugatmagan foydalanuvchi yozuvi muddatsiz qolmaydi —
     * {@code onboarding-retention-days} (standart 30) kun faolliksiz bo'lsa psevdonimlanadi va yopiladi.
     */
    public int cleanupStaleOnboarding() {
        int days = props.deletion() == null || props.deletion().onboardingRetentionDays() <= 0 ? 30
                : props.deletion().onboardingRetentionDays();
        List<Long> stale = jdbc.queryForList("""
                select u.id from users u where u.status = 'ONBOARDING' and u.purged_at is null
                  and coalesce(u.last_seen_at, u.updated_at) < now() - make_interval(days => ?)
                  and not exists (select 1 from businesses b where b.owner_user_id = u.id)""", Long.class, days);
        int n = 0;
        for (Long userId : stale) {
            try {
                Boolean ok = tx.execute(st -> {
                    String s = jdbc.query("select status from users where id = ? for update", rs -> rs.next() ? rs.getString(1) : null, userId);
                    if (!"ONBOARDING".equals(s)) return false;
                    purger.purge(userId, "deletion-job", "onboarding not completed for " + days + " days");
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) n++;
            } catch (RuntimeException e) {
                log.error("Onboarding cleanup failed for user {}", userId, e);
            }
        }
        if (n > 0) log.info("Removed {} stale onboarding records", n);
        return n;
    }
}
