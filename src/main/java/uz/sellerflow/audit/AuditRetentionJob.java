package uz.sellerflow.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LOW (DB): audit_events dan saqlash muddati ({@code sellerflow.audit.retention-days}, standart 365) o'tganlarini
 * o'chiradi. Partiyalab (10 000 tadan) — uzun qulf bo'lmasin. 0 yoki manfiy — o'chirilmaydi.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
    static final int BATCH = 10_000;

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public AuditRetentionJob(JdbcTemplate jdbc, @Value("${sellerflow.audit.retention-days:365}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Tashkent")
    public int run() {
        if (retentionDays <= 0) return 0;
        int total = 0;
        while (true) {
            int n = jdbc.update("""
                    delete from audit_events where id in (
                      select id from audit_events where created_at < now() - make_interval(days => ?) limit ?)""",
                    retentionDays, BATCH);
            total += n;
            if (n < BATCH) break;
        }
        if (total > 0) log.info("Audit retention: deleted {} events older than {} days", total, retentionDays);
        return total;
    }
}
