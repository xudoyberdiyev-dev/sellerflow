package uz.sellerflow.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M-10 / M-22: poller offset boshqaruvi va xatolar. */
class TelegramPollerTest {

    static Tg.Update upd(long id) {
        return new Tg.Update(id, new Tg.Message(id, new Tg.User(7, false, "A", null, null), new Tg.Chat(7, "private"), "x"), null);
    }

    static class FakeGateway implements TelegramGateway {
        final Deque<Object> script = new ArrayDeque<>();
        final List<Long> offsets = new ArrayList<>();
        @Override public void sendMessage(long chatId, String html, Tg.Markup markup) {}
        @Override public void clearInlineKeyboard(long chatId, long messageId) {}
        @Override public void answerCallback(String callbackId, String text) {}
        @SuppressWarnings("unchecked")
        @Override public List<Tg.Update> getUpdates(long offset, int timeoutSeconds) {
            offsets.add(offset);
            Object next = script.isEmpty() ? List.of() : script.poll();
            if (next instanceof RuntimeException e) throw e;
            return (List<Tg.Update>) next;
        }
    }

    @Test
    void offsetAdvances_pastHandledAndFailedUpdates() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(10), upd(11), upd(12)));
        List<Long> handled = new ArrayList<>();
        BotStatus st = new BotStatus();
        TelegramPoller p = new TelegramPoller(null, g, u -> {
            handled.add(u.updateId());
            if (u.updateId() == 11) throw new IllegalStateException("bug"); // tashlanadi, offset suriladi
        }, 1, st);
        assertThat(p.pollOnce(0)).isEqualTo(13);
        assertThat(handled).containsExactly(10L, 11L, 12L);
        assertThat(st.getProcessedUpdates()).isEqualTo(2);
        assertThat(st.getLastError()).contains("IllegalStateException");
        assertThat(st.getLastPollOkAt()).isNotNull();
    }

    @Test
    void transientDbFailure_doesNotAdvanceOffset_updateIsRedelivered() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(10), upd(11), upd(12)));
        g.script.add(List.of(upd(11), upd(12)));
        List<Long> handled = new ArrayList<>();
        boolean[] dbDown = {true};
        TelegramPoller p = new TelegramPoller(null, g, u -> {
            if (u.updateId() == 11 && dbDown[0]) throw new DataAccessResourceFailureException("connection refused");
            handled.add(u.updateId());
        }, 1, new BotStatus());
        assertThatThrownBy(() -> p.pollOnce(0)).isInstanceOf(DataAccessResourceFailureException.class);
        dbDown[0] = false;
        // loop offset'ni yangilamaydi — 0 bilan qayta so'raydi (Telegram 10 ni ham qayta berishi mumkin; dispatcher dublikatni o'tkazadi)
        assertThat(p.pollOnce(0)).isEqualTo(13);
        assertThat(handled).containsExactly(10L, 11L, 12L);
    }

    @Test
    void pollingError_propagates_forBackoff() {
        FakeGateway g = new FakeGateway();
        g.script.add(new org.springframework.web.client.RestClientException("getUpdates failed: Unauthorized"));
        TelegramPoller p = new TelegramPoller(null, g, u -> {}, 1, new BotStatus());
        assertThatThrownBy(() -> p.pollOnce(5)).isInstanceOf(org.springframework.web.client.RestClientException.class);
        assertThat(g.offsets).containsExactly(5L);
    }

    @Test
    void stop_beforeStart_isSafe() {
        TelegramPoller p = new TelegramPoller(null, new FakeGateway(), u -> {}, 1, new BotStatus());
        p.stop();
        assertThat(p.pollOnce(3)).isEqualTo(3);
    }
}
