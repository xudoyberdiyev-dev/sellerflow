package uz.sellerflow.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D-5: bot faolligini yozadi — {@code users.last_seen_at} (daqiqasiga ko'pi bilan bir marta) va
 * {@code user_activity_days} (kuniga bitta qator, Asia/Tashkent sanasi). Xato bot ishini to'xtatmaydi.
 */
@Component
public class ActivityTracker {

    private static final Logger log = LoggerFactory.getLogger(ActivityTracker.class);
    private final JdbcTemplate jdbc;

    public ActivityTracker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void touch(long userId) {
        try {
            jdbc.update("""
                    update users set last_seen_at = now()
                    where id = ? and (last_seen_at is null or last_seen_at < now() - interval '1 minute')""", userId);
            jdbc.update("""
                    insert into user_activity_days (user_id, day)
                    values (?, (now() at time zone 'Asia/Tashkent')::date) on conflict do nothing""", userId);
        } catch (RuntimeException e) {
            log.warn("activity tracking failed: {}", e.getClass().getSimpleName());
        }
    }
}
