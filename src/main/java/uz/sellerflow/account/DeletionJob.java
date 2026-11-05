package uz.sellerflow.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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

    public DeletionJob(JdbcTemplate jdbc, AuditService audit, SellerFlowProperties props) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Tashkent")
    @Transactional
    public int run() {
        int grace = props.deletion() == null ? 30 : props.deletion().graceDays();
        boolean purge = props.deletion() != null && props.deletion().purgeData();
        List<Long> due = jdbc.queryForList("""
                select id from users where status = 'DELETION_REQUESTED'
                  and deletion_requested_at < now() - make_interval(days => ?)""", Long.class, grace);
        for (Long userId : due) {
            Long businessId = jdbc.query("select id from businesses where owner_user_id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, userId);
            jdbc.update("delete from telegram_flows where user_id = ?", userId);
            if (businessId != null) {
                jdbc.update("update businesses set business_name = null, display_name = null, updated_at = now() where id = ?", businessId);
                jdbc.update("update access_grants set status = 'REVOKED', revoked_at = now(), revoked_by = 'SYSTEM' "
                        + "where business_id = ? and status in ('REQUESTED','APPROVED')", businessId);
                if (purge) {
                    jdbc.update("delete from calculation_history where business_id = ?", businessId);
                    jdbc.update("delete from products where business_id = ?", businessId);
                }
            }
            jdbc.update("update users set status = 'CLOSED', updated_at = now() where id = ?", userId);
            audit.record(Actor.SYSTEM, "deletion-job", businessId, "ACCOUNT_CLOSED", "USER", userId,
                    "grace " + grace + " days; commercial data " + (purge ? "purged" : "retained (pending legal policy)"));
        }
        if (!due.isEmpty()) log.info("Closed {} accounts after deletion grace period", due.size());
        return due.size();
    }
}
