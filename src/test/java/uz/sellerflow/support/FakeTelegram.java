package uz.sellerflow.support;

import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.TelegramGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Telegram'ga yuborilgan xabarlarni yozib oladigan soxta gateway. */
public class FakeTelegram implements TelegramGateway {

    public record Sent(long chatId, String text, Tg.Markup markup) {
        public List<Tg.InlineButton> buttons() {
            if (markup instanceof Tg.InlineKeyboard k) {
                List<Tg.InlineButton> all = new ArrayList<>();
                k.rows().forEach(all::addAll);
                return all;
            }
            return List.of();
        }
    }

    private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());

    private volatile RuntimeException failNext;

    /** Keyingi sendMessage shu xato bilan tugaydi (bir marta). */
    public void failNextSend(RuntimeException e) { failNext = e; }

    @Override
    public void sendMessage(long chatId, String html, Tg.Markup markup) {
        RuntimeException f = failNext;
        if (f != null) {
            failNext = null;
            throw f;
        }
        sent.add(new Sent(chatId, html, markup));
    }

    @Override
    public void clearInlineKeyboard(long chatId, long messageId) {}

    @Override
    public void answerCallback(String callbackId, String text) {}

    @Override
    public List<Tg.Update> getUpdates(long offset, int timeoutSeconds) { return List.of(); }

    public List<Sent> forChat(long chatId) {
        synchronized (sent) {
            return sent.stream().filter(s -> s.chatId() == chatId).toList();
        }
    }

    public Sent last(long chatId) {
        List<Sent> l = forChat(chatId);
        if (l.isEmpty()) throw new AssertionError("No messages for chat " + chatId);
        return l.get(l.size() - 1);
    }

    /** Eng oxirgi xabarlardan berilgan matnli inline tugmani topadi. */
    public Optional<Tg.InlineButton> findButton(long chatId, String textContains) {
        List<Sent> l = forChat(chatId);
        for (int i = l.size() - 1; i >= 0; i--) {
            for (Tg.InlineButton b : l.get(i).buttons()) {
                if (b.text().contains(textContains) && b.callbackData() != null) return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public void clear() { sent.clear(); failNext = null; }
}
