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
    private static boolean embedded;

    /** JDBC URL'dagi baza nomi: jdbc:postgresql://host:port/NAME?params → NAME. */
    static String databaseName(String jdbcUrl) {
        String s = jdbcUrl;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    /** LOW (testlar): testlar TRUNCATE qiladi — tashqi baza nomi "_test" bilan tugamasa to'xtaymiz. */
    static void requireTestDatabase(String jdbcUrl) {
        if (!databaseName(jdbcUrl).endsWith("_test")) {
            throw new IllegalStateException("SELLERFLOW_TEST_DB_URL must point to a database whose name ends with '_test' "
                    + "(tests TRUNCATE tables): " + databaseName(jdbcUrl));
        }
    }

    public static boolean isEmbedded() { init(); return embedded; }

    public static synchronized void init() {
        if (url != null) return;
        String env = System.getenv("SELLERFLOW_TEST_DB_URL");
        if (env != null && !env.isBlank()) {
            requireTestDatabase(env);
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
            embedded = true;
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
