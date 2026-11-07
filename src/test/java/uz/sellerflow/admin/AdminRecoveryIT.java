package uz.sellerflow.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.sellerflow.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** M-7: break-glass tiklash (ADMIN_RECOVERY_EMAIL / ADMIN_RECOVERY_PASSWORD). */
class AdminRecoveryIT extends IntegrationTest {

    @Autowired AdminAuthService auth;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;

    @Test
    void recovery_unlocksResets2faAndRevokesSessions_onlyOncePerPassword() {
        String email = "locked" + System.nanoTime() + "@sellerflow.test";
        jdbc.update("""
                insert into admins (email, password_hash, role, status, totp_secret, totp_enabled, must_change_password,
                                    failed_attempts, locked_until, totp_last_step, created_at)
                values (?, 'x', 'SUPPORT', 'DISABLED', 'JBSWY3DPEHPK3PXP', true, false, 3, now() + interval '1 hour', 99, now())""", email);
        long id = jdbc.queryForObject("select id from admins where email = ?", Long.class, email);
        jdbc.update("insert into admin_sessions (token_hash, admin_id, kind, created_at, expires_at, revoked) values (?, ?, 'FULL', now(), now() + interval '1 hour', false)",
                "h" + System.nanoTime(), id);

        assertThat((boolean) tx.execute(s -> auth.recover(email, "recovery-pass-123456"))).isTrue();

        var row = jdbc.queryForMap("select role, status, totp_enabled, totp_secret, failed_attempts, locked_until, must_change_password from admins where id = ?", id);
        assertThat(row.get("role")).isEqualTo("SUPERADMIN");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("totp_enabled")).isEqualTo(false);
        assertThat(row.get("totp_secret")).isNull();
        assertThat(row.get("failed_attempts")).isEqualTo(0);
        assertThat(row.get("locked_until")).isNull();
        assertThat(row.get("must_change_password")).isEqualTo(true);
        assertThat(jdbc.queryForObject("select count(*) from admin_sessions where admin_id = ? and not revoked", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ADMIN_RECOVERED' and entity_id = ?", Long.class, String.valueOf(id))).isEqualTo(1);

        // Login: parol ishlaydi, 2FA qayta ulanadi
        var r = auth.login(email, "recovery-pass-123456", null);
        assertThat(r.status()).isEqualTo("TOTP_SETUP_REQUIRED");

        // Env o'chirilmay qolsa — qayta qo'llanmaydi
        jdbc.update("update admins set totp_enabled = true where id = ?", id);
        assertThat((boolean) tx.execute(s -> auth.recover(email, "recovery-pass-123456"))).isFalse();
        assertThat(jdbc.queryForObject("select totp_enabled from admins where id = ?", Boolean.class, id)).isTrue();

        // Qisqa parol rad etiladi
        assertThat((boolean) tx.execute(s -> auth.recover(email, "short"))).isFalse();
    }

    @Test
    void recovery_createsSuperadmin_whenEmailUnknown() {
        String email = "new" + System.nanoTime() + "@sellerflow.test";
        assertThat((boolean) tx.execute(s -> auth.recover(email, "recovery-pass-123456"))).isTrue();
        assertThat(jdbc.queryForObject("select role from admins where lower(email) = lower(?)", String.class, email)).isEqualTo("SUPERADMIN");
    }
}
