package uz.sellerflow.telegram;

import java.util.List;

/** Telegram'ga chiquvchi chaqiruvlar. Testlarda soxta implementatsiya bilan almashtiriladi. */
public interface TelegramGateway {

    void sendMessage(long chatId, String html, Tg.Markup markup);

    /** Bosilgan xabardagi inline tugmalarni olib tashlash (eski tugmalarni qayta bosishni kamaytiradi). */
    void clearInlineKeyboard(long chatId, long messageId);

    void answerCallback(String callbackId, String text);

    /** V2: foydalanuvchi xabarini o'chirish (masalan, maxfiy kalit yozilgan xabar). Xato bo'lsa — jim. */
    default void deleteMessage(long chatId, long messageId) {}

    /** V2: yuborilgan faylni yuklab olish (import). maxBytes dan katta bo'lsa — IllegalArgumentException("FILE_TOO_LARGE"). */
    default byte[] downloadFile(String fileId, long maxBytes) {
        throw new UnsupportedOperationException("downloadFile");
    }

    /** V1+: fayl yuborish (CSV eksport). Standart — qo'llab-quvvatlanmaydi (Noop/test stublari). */
    default void sendDocument(long chatId, String fileName, byte[] content, String caption) {
        throw new UnsupportedOperationException("sendDocument");
    }

    List<Tg.Update> getUpdates(long offset, int timeoutSeconds);
}
