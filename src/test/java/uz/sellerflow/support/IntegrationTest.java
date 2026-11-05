package uz.sellerflow.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uz.sellerflow.bot.BotDispatcher;
import uz.sellerflow.telegram.Tg;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sellerflow.telegram.bot-token=",
        "sellerflow.admin.api-key=test-admin-key"
})
@Import(IntegrationTest.Config.class)
public abstract class IntegrationTest {

    private static final AtomicLong UPDATE_IDS = new AtomicLong(1_000_000);
    private static final AtomicLong USER_IDS = new AtomicLong(500_000_000);

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDatabase::url);
        r.add("spring.datasource.username", TestDatabase::user);
        r.add("spring.datasource.password", TestDatabase::password);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public FakeTelegram fakeTelegram() { return new FakeTelegram(); }
    }

    @Autowired protected BotDispatcher dispatcher;
    @Autowired protected FakeTelegram tg;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        jdbc.execute("truncate table audit_events, calculation_history, products, telegram_flows, consents, businesses, users, processed_updates restart identity cascade");
        jdbc.update("update support_settings set enabled = false, contact_label = null, contact_url = null");
        tg.clear();
    }

    protected static long newUserId() { return USER_IDS.incrementAndGet(); }

    // ------------------------------------------------------------ driver

    protected void text(long userId, String text) {
        Tg.User from = new Tg.User(userId, false, "Test", null, "uz");
        Tg.Message m = new Tg.Message(UPDATE_IDS.incrementAndGet(), from, new Tg.Chat(userId, "private"), text);
        dispatcher.handle(new Tg.Update(UPDATE_IDS.incrementAndGet(), m, null));
    }

    protected void callback(long userId, String data) {
        Tg.User from = new Tg.User(userId, false, "Test", null, "uz");
        Tg.Message source = new Tg.Message(1, null, new Tg.Chat(userId, "private"), "x");
        Tg.CallbackQuery q = new Tg.CallbackQuery("cb" + UPDATE_IDS.incrementAndGet(), from, source, data);
        dispatcher.handle(new Tg.Update(UPDATE_IDS.incrementAndGet(), null, q));
    }

    /** Oxirgi xabarlardagi tugmani matni bo'yicha bosish. */
    protected void click(long userId, String buttonText) {
        String data = tg.findButton(userId, buttonText)
                .orElseThrow(() -> new AssertionError("Button not found: " + buttonText + "\nLast: " + tg.last(userId).text()))
                .callbackData();
        callback(userId, data);
    }

    protected String lastText(long userId) { return tg.last(userId).text(); }

    /** To'liq onboarding: /start -> rozilik -> Uzum -> nomlarsiz -> faollashtirish. */
    protected long onboardedUser() {
        long u = newUserId();
        text(u, "/start");
        click(u, "Boshlash");
        click(u, "Roziman");
        click(u, "Uzum Market");
        click(u, "O‘tkazib yuborish");
        click(u, "O‘tkazib yuborish");
        click(u, "Faollashtirish");
        assertThat(lastText(u)).contains("faollashtirildi");
        tg.clear();
        return u;
    }
}
