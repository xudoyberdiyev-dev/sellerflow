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

    public DeletionJob(JdbcTemplate jdbc, AuditService audit, SellerFlowProperties props,
                       org.springframework.transaction.PlatformTransactionManager tm) {
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
                tx.executeWithoutResult(st -> closeOne(userId, grace, purge));
                closed++;
            } catch (RuntimeException e) {
                log.error("Deletion job failed for user {} — will retry next run", userId, e);
            }
        }
        if (closed > 0) log.info("Closed {} accounts after deletion grace period", closed);
        if (purge) purgePreviouslyClosed();
        return closed;
    }

    private void closeOne(Long userId, int grace, boolean purge) {
        Long businessId = jdbc.query("select id from businesses where owner_user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
        jdbc.update("delete from telegram_flows where user_id = ?", userId);
        if (businessId != null) {
            jdbc.update("update businesses set business_name = null, display_name = null, updated_at = now() where id = ?", businessId);
            jdbc.update("update access_grants set status = 'REVOKED', revoked_at = now(), revoked_by = 'SYSTEM', version = version + 1 "
                    + "where business_id = ? and status in ('REQUESTED','APPROVED')", businessId);
            if (purge) {
                jdbc.update("delete from calculation_history where business_id = ?", businessId);
                jdbc.update("delete from products where business_id = ?", businessId);
            }
        }
        if (purge) {
            // M-23: purge yoqilgan bo'lsa Telegram ID ham psevdonimga almashtiriladi (shaxs bilan bog'lanish uziladi)
            jdbc.update("update users set status = 'CLOSED', telegram_user_id = ?, updated_at = now(), version = version + 1 where id = ?",
                    AccountService.pseudonymousTelegramId(), userId);
        } else {
            jdbc.update("update users set status = 'CLOSED', updated_at = now(), version = version + 1 where id = ?", userId);
        }
        audit.record(Actor.SYSTEM, "deletion-job", businessId, "ACCOUNT_CLOSED", "USER", userId,
                "grace " + grace + " days; commercial data " + (purge ? "purged, telegram id pseudonymized" : "retained (pending legal policy)"));
    }

    /**
     * LOW (maxfiylik): DELETION_PURGE_DATA keyinroq yoqilsa — avval (purge'siz) yopilgan akkauntlar ham tozalanadi:
     * tijoriy ma'lumot o'chiriladi va Telegram ID psevdonimga almashtiriladi.
     */
    private void purgePreviouslyClosed() {
        List<Long> old = jdbc.queryForList("select id from users where status = 'CLOSED' and telegram_user_id > 0", Long.class);
        for (Long userId : old) {
            tx.executeWithoutResult(st -> purgeOne(userId));
        }
        if (!old.isEmpty()) log.info("Purged {} previously closed accounts", old.size());
    }

    private void purgeOne(Long userId) {
        Long businessId = jdbc.query("select id from businesses where owner_user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
        if (businessId != null) {
            jdbc.update("delete from calculation_history where business_id = ?", businessId);
            jdbc.update("delete from products where business_id = ?", businessId);
        }
        jdbc.update("delete from telegram_flows where user_id = ?", userId);
        jdbc.update("update users set telegram_user_id = ?, updated_at = now(), version = version + 1 where id = ?",
                AccountService.pseudonymousTelegramId(), userId);
        audit.record(Actor.SYSTEM, "deletion-job", businessId, "ACCOUNT_PURGED", "USER", userId,
                "previously closed account purged after DELETION_PURGE_DATA was enabled");
    }
}
