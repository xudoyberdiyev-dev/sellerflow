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
        }, 1, st, 1);
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
        }, 1, new BotStatus(), 1);
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
        TelegramPoller p = new TelegramPoller(null, g, u -> {}, 1, new BotStatus(), 1);
        assertThatThrownBy(() -> p.pollOnce(5)).isInstanceOf(org.springframework.web.client.RestClientException.class);
        assertThat(g.offsets).containsExactly(5L);
    }

    static Tg.Update updFrom(long id, long user) {
        return new Tg.Update(id, new Tg.Message(id, new Tg.User(user, false, "A", null, null), new Tg.Chat(user, "private"), "x"), null);
    }

    /** M-9: slow foydalanuvchi boshqalarni bloklamaydi; bitta foydalanuvchi tartibi saqlanadi. */
    @Test
    void parallelWorkers_preservePerUserOrder_andDoNotBlockOthers() throws Exception {
        FakeGateway g = new FakeGateway();
        List<Tg.Update> batch = new ArrayList<>();
        long id = 100;
        for (int i = 0; i < 5; i++) {
            batch.add(updFrom(id++, 1)); // sekin foydalanuvchi
            batch.add(updFrom(id++, 2));
            batch.add(updFrom(id++, 3));
        }
        g.script.add(batch);
        var perUser = new java.util.concurrent.ConcurrentHashMap<Long, List<Long>>();
        var user2DoneAt = new java.util.concurrent.atomic.AtomicLong();
        var user1DoneAt = new java.util.concurrent.atomic.AtomicLong();
        TelegramPoller p = new TelegramPoller(null, g, u -> {
            long user = u.message().from().id();
            if (user == 1) {
                try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            perUser.computeIfAbsent(user, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(u.updateId());
            if (user == 2) user2DoneAt.set(System.nanoTime());
            if (user == 1) user1DoneAt.set(System.nanoTime());
        }, 1, new BotStatus(), 4);
        assertThat(p.pollOnce(0)).isEqualTo(id);
        for (long user : new long[]{1, 2, 3}) {
            assertThat(perUser.get(user)).hasSize(5).isSorted();
        }
        assertThat(user2DoneAt.get()).isLessThan(user1DoneAt.get()); // 2-foydalanuvchi sekin 1-ni kutmadi
        p.stop();
    }

    @Test
    void parallelWorkers_transientFailureInOnePartition_offsetNotAdvanced() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(updFrom(1, 1), updFrom(2, 2), updFrom(3, 1)));
        List<Long> handled = java.util.Collections.synchronizedList(new ArrayList<>());
        TelegramPoller p = new TelegramPoller(null, g, u -> {
            if (u.updateId() == 1) throw new DataAccessResourceFailureException("down");
            handled.add(u.updateId());
        }, 1, new BotStatus(), 4);
        assertThatThrownBy(() -> p.pollOnce(0)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(handled).containsExactly(2L); // 1-foydalanuvchining 3-update'i tartib uchun to'xtatildi
        p.stop();
    }

    @Test
    void backoff_isExponential_cappedAt30s() {
        int b = 1000;
        List<Integer> seq = new ArrayList<>();
        for (int i = 0; i < 7; i++) { b = TelegramPoller.nextBackoff(b); seq.add(b); }
        assertThat(seq).containsExactly(2000, 4000, 8000, 16000, 30000, 30000, 30000);
    }

    @Test
    void emptyBatch_keepsOffset_andMarksPollOk() {
        FakeGateway g = new FakeGateway();
        BotStatus st = new BotStatus();
        TelegramPoller p = new TelegramPoller(null, g, u -> { throw new AssertionError("no updates"); }, 1, st, 2);
        assertThat(p.pollOnce(42)).isEqualTo(42);
        assertThat(g.offsets).containsExactly(42L);
        assertThat(st.getLastPollOkAt()).isNotNull();
        p.stop();
    }

    @Test
    void callbackUpdates_arePartitionedByFromUser() {
        Tg.CallbackQuery q = new Tg.CallbackQuery("c", new Tg.User(99, false, "B", null, null),
                new Tg.Message(1, null, new Tg.Chat(99, "private"), "x"), "m");
        assertThat(TelegramPoller.userKey(new Tg.Update(5, null, q))).isEqualTo(99);
        assertThat(TelegramPoller.userKey(new Tg.Update(6, null, null))).isEqualTo(6);
    }

    @Test
    void stop_beforeStart_isSafe() {
        TelegramPoller p = new TelegramPoller(null, new FakeGateway(), u -> {}, 1, new BotStatus(), 1);
        p.stop();
        assertThat(p.pollOnce(3)).isEqualTo(3);
    }
}
