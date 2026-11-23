package uz.sellerflow.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.common.SecretBox;

/** Startda: ochiq saqlangan admin TOTP sirlarini shifrlaydi (kalit bo'lsa) yoki kalit yo'qligi haqida ogohlantiradi. */
@Component
public class TotpSecretMigrator {

    private static final Logger log = LoggerFactory.getLogger(TotpSecretMigrator.class);

    private final JdbcTemplate jdbc;
    private final SecretBox box;
    private final TotpSecretConverter converter;

    public TotpSecretMigrator(JdbcTemplate jdbc, SecretBox box, TotpSecretConverter converter) {
        this.jdbc = jdbc;
        this.box = box;
        this.converter = converter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        if (!box.enabled()) {
            log.warn("SELLERFLOW_SECRETS_KEY berilmagan: admin TOTP sirlari bazada shifrlanmagan, Uzum ulanishi o'chiq");
            return;
        }
        var rows = jdbc.query("select id, totp_secret from admins where totp_secret is not null and totp_secret not like 'enc:v1:%'",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2)});
        for (Object[] r : rows) {
            jdbc.update("update admins set totp_secret = ? where id = ? and totp_secret = ?",
                    converter.convertToDatabaseColumn((String) r[1]), r[0], r[1]);
        }
        if (!rows.isEmpty()) log.info("Admin TOTP sirlari shifrlandi: {}", rows.size());
    }
}
