package uz.sellerflow.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;

/**
 * N-9/N-10/N-11: akkaunt ma'lumotini to'liq tozalash — bitta joyda (DeletionJob, qayta ochish, admin, onboarding retention).
 * O'chiriladi: mahsulotlar, saqlangan hisoblashlar, qoralamalar, faollik (V8), biznes nomi va ism; faol ruxsatlar bekor.
 * Telegram ID tasodifiy manfiy psevdonimga almashtiriladi, akkaunt CLOSED, {@code purged_at} qo'yiladi.
 * Saqlanadi (dalil): rozilik yozuvlari va audit jurnali (tijoriy qiymatlarsiz). Chaqiruvchi tranzaksiyasida ishlaydi.
 */
@Component
public class AccountPurger {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AccountPurger(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public void purge(long userId, String actor, String reason) {
        Long businessId = jdbc.query("select id from businesses where owner_user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
        if (businessId != null) {
            jdbc.update("delete from calculation_history where business_id = ?", businessId);
            jdbc.update("delete from products where business_id = ?", businessId);
            jdbc.update("update businesses set business_name = null, display_name = null, updated_at = now() where id = ?", businessId);
            jdbc.update("update access_grants set status = 'REVOKED', revoked_at = now(), revoked_by = 'SYSTEM', version = version + 1 "
                    + "where business_id = ? and status in ('REQUESTED','APPROVED')", businessId);
        }
        clearActivity(userId);
        jdbc.update("""
                update users set status = 'CLOSED', telegram_user_id = ?, purged_at = now(), status_before_deletion = null,
                  updated_at = now(), version = version + 1 where id = ?""", AccountService.pseudonymousTelegramId(), userId);
        audit.record(Actor.SYSTEM, actor, businessId, "ACCOUNT_PURGED", "USER", userId, reason);
    }

    /** Qoralamalar va faollik izlari (V8) — akkaunt yopilganda har doim tozalanadi. */
    public void clearActivity(long userId) {
        jdbc.update("delete from telegram_flows where user_id = ?", userId);
        jdbc.update("delete from user_activity_days where user_id = ?", userId);
        jdbc.update("update users set last_seen_at = null where id = ?", userId);
    }
}
