package uz.sellerflow.telegram;

import java.util.List;

/** Telegram'ga chiquvchi chaqiruvlar. Testlarda soxta implementatsiya bilan almashtiriladi. */
public interface TelegramGateway {

    void sendMessage(long chatId, String html, Tg.Markup markup);

    /** Bosilgan xabardagi inline tugmalarni olib tashlash (eski tugmalarni qayta bosishni kamaytiradi). */
    void clearInlineKeyboard(long chatId, long messageId);

    void answerCallback(String callbackId, String text);

    List<Tg.Update> getUpdates(long offset, int timeoutSeconds);
}
