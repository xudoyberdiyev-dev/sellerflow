package uz.sellerflow.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;

/**
 * Testlar uchun haqiqiy PostgreSQL.
 * SELLERFLOW_TEST_DB_URL berilgan bo'lsa — o'sha baza; aks holda Docker'siz embedded PostgreSQL
 * (zonky; root foydalanuvchi ostida ishlamaydi — CI/root muhitida SELLERFLOW_TEST_DB_URL bering).
 */
public final class TestDatabase {
    private TestDatabase() {}

    private static String url;
    private static String user;
    private static String password;

    public static synchronized void init() {
        if (url != null) return;
        String env = System.getenv("SELLERFLOW_TEST_DB_URL");
        if (env != null && !env.isBlank()) {
            url = env;
            user = System.getenv().getOrDefault("SELLERFLOW_TEST_DB_USER", "postgres");
            password = System.getenv().getOrDefault("SELLERFLOW_TEST_DB_PASSWORD", "");
            return;
        }
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { pg.close(); } catch (IOException ignored) { }
            }));
            url = pg.getJdbcUrl("postgres", "postgres");
            user = "postgres";
            password = "postgres";
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
        }
    }

    public static String url() { init(); return url; }
    public static String user() { init(); return user; }
    public static String password() { init(); return password; }
}
