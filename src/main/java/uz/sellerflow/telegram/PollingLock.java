package uz.sellerflow.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * LOW: ikkinchi ilova nusxasi (bitta baza, bitta token) bir vaqtda polling qila olmasin — PostgreSQL session
 * advisory lock. Lock alohida, doimiy ulanishda saqlanadi; ulanish uzilsa lock ham bo'shaydi va qayta olinadi.
 */
@Component
public class PollingLock {

    private static final Logger log = LoggerFactory.getLogger(PollingLock.class);
    static final long KEY = 0x5E11_F10A_7E1EL; // «SellerFlow telegram» uchun ixtiyoriy doimiy

    private final DataSource dataSource;
    private Connection conn;
    private boolean warned;

    public PollingLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Lock shu nusxada bo'lsa — true (kerak bo'lsa oladi). */
    public synchronized boolean ensureHeld() {
        try {
            if (conn != null && conn.isValid(2)) return true;
            close();
            Connection c = dataSource.getConnection();
            c.setAutoCommit(true);
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select pg_try_advisory_lock(" + KEY + ")")) {
                if (rs.next() && rs.getBoolean(1)) {
                    conn = c;
                    if (warned) log.info("Telegram polling lock acquired");
                    warned = false;
                    return true;
                }
            }
            c.close();
            if (!warned) {
                log.error("Another SellerFlow instance is already polling Telegram (advisory lock busy) — this instance waits");
                warned = true;
            }
            return false;
        } catch (SQLException e) {
            close();
            log.warn("Polling lock check failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @jakarta.annotation.PreDestroy
    public synchronized void close() {
        if (conn == null) return;
        try {
            conn.close(); // session tugashi bilan advisory lock bo'shaydi
        } catch (SQLException ignored) {
            // ulanish allaqachon yopiq
        }
        conn = null;
    }
}
