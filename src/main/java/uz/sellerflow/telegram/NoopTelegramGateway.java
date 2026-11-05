package uz.sellerflow.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/** TELEGRAM_BOT_TOKEN berilmagan bo'lsa: ilova ishga tushadi (admin API, health), bot o'chiq. */
@Component
@ConditionalOnExpression("'${sellerflow.telegram.bot-token:}' == ''")
public class NoopTelegramGateway implements TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(NoopTelegramGateway.class);

    public NoopTelegramGateway() {
        log.warn("TELEGRAM_BOT_TOKEN is not set — Telegram bot is disabled");
    }

    @Override
    public void sendMessage(long chatId, String html, Tg.Markup markup) {}

    @Override
    public void clearInlineKeyboard(long chatId, long messageId) {}

    @Override
    public void answerCallback(String callbackId, String text) {}

    @Override
    public List<Tg.Update> getUpdates(long offset, int timeoutSeconds) {
        return List.of();
    }
}
