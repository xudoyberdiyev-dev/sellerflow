package uz.sellerflow.telegram;

import java.util.List;

/** Telegram'ga chiquvchi chaqiruvlar. Testlarda soxta implementatsiya bilan almashtiriladi. */
public interface TelegramGateway {

    void sendMessage(long chatId, String html, Tg.Markup markup);

    /** Bosilgan xabardagi inline tugmalarni olib tashlash (eski tugmalarni qayta bosishni kamaytiradi). */
    void clearInlineKeyboard(long chatId, long messageId);

    void answerCallback(String callbackId, String text);

    /** V1+: fayl yuborish (CSV eksport). Standart — qo'llab-quvvatlanmaydi (Noop/test stublari). */
    default void sendDocument(long chatId, String fileName, byte[] content, String caption) {
        throw new UnsupportedOperationException("sendDocument");
    }

    List<Tg.Update> getUpdates(long offset, int timeoutSeconds);
}
