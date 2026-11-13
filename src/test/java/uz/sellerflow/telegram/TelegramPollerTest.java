package uz.sellerflow.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M-10 / M-22 / N-7 / N-12: poller offset boshqaruvi, navbatlar va xatolar. */
class TelegramPollerTest {

    static Tg.Update upd(long id) {
        return new Tg.Update(id, new Tg.Message(id, new Tg.User(7, false, "A", null, null), new Tg.Chat(7, "private"), "x"), null);
    }

    static Tg.Update updFrom(long id, long user) {
        return new Tg.Update(id, new Tg.Message(id, new Tg.User(user, false, "A", null, null), new Tg.Chat(user, "private"), "x"), null);
    }

    static class FakeGateway implements TelegramGateway {
        final Deque<Object> script = new ArrayDeque<>();
        final List<Long> offsets = java.util.Collections.synchronizedList(new ArrayList<>());
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

    private static TelegramPoller poller(FakeGateway g, UpdateHandler h, BotStatus st, int workers) {
        TelegramPoller p = new TelegramPoller(null, g, h, 1, st, workers);
        p.setSleeper(ms -> {});
        return p;
    }

    @Test
    void offsetAdvances_pastHandledAndFailedUpdates() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(10), upd(11), upd(12)));
        List<Long> handled = java.util.Collections.synchronizedList(new ArrayList<>());
        BotStatus st = new BotStatus();
        TelegramPoller p = poller(g, u -> {
            handled.add(u.updateId());
            if (u.updateId() == 11) throw new IllegalStateException("bug"); // dead-letter, offset suriladi
        }, st, 1);
        long o = p.pollOnce(0);
        assertThat(o).isEqualTo(10); // hali ishlanmoqda — offset tasdiqlanmaydi
        assertThat(p.awaitIdle(2000)).isTrue();
        assertThat(p.pollOnce(o)).isEqualTo(13);
        assertThat(handled).containsExactly(10L, 11L, 12L);
        assertThat(st.getProcessedUpdates()).isEqualTo(2);
        assertThat(st.getLastError()).contains("IllegalStateException");
        assertThat(st.getLastPollOkAt()).isNotNull();
        p.stop();
    }

    /** M-10: DB uzilsa update qayta uriniladi (offset surilmaydi), tiklangach ishlanadi. */
    @Test
    void transientDbFailure_isRetried_offsetHeldUntilDone() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(10), upd(11), upd(12)));
        List<Long> handled = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        TelegramPoller p = poller(g, u -> {
            if (u.updateId() == 11 && failures.getAndIncrement() < 3) throw new DataAccessResourceFailureException("connection refused");
            handled.add(u.updateId());
        }, new BotStatus(), 1);
        assertThat(p.pollOnce(0)).isEqualTo(10);
        assertThat(p.awaitIdle(2000)).isTrue();
        assertThat(handled).containsExactly(10L, 11L, 12L); // tartib saqlandi
        assertThat(failures.get()).isEqualTo(4);
        assertThat(p.pollOnce(10)).isEqualTo(13);
        p.stop();
    }

    /** N-7: «zaharli» update cheksiz aylanmaydi — retry oynasidan keyin dead-letter, keyingilar ishlanadi. */
    @Test
    void poisonTransientUpdate_isDeadLetteredAfterRetryWindow() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(1), upd(2)));
        List<Long> handled = java.util.Collections.synchronizedList(new ArrayList<>());
        BotStatus st = new BotStatus();
        TelegramPoller p = poller(g, u -> {
            if (u.updateId() == 1) throw new DataAccessResourceFailureException("always");
            handled.add(u.updateId());
        }, st, 1);
        p.setRetryWindow(java.time.Duration.ofMillis(50));
        p.pollOnce(0);
        assertThat(p.awaitIdle(3000)).isTrue();
        assertThat(handled).containsExactly(2L);
        assertThat(p.pollOnce(0)).isEqualTo(3);
        assertThat(st.getLastError()).contains("DataAccessResourceFailureException");
        p.stop();
    }

    /** N-7: Error (masalan, StackOverflowError emas — AssertionError) worker'ni o'ldirmaydi va cheksiz takrorlanmaydi. */
    @Test
    void errorInHandler_isDeadLettered_workerSurvives() {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(upd(1), upd(2)));
        List<Long> handled = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        TelegramPoller p = poller(g, u -> {
            calls.incrementAndGet();
            if (u.updateId() == 1) throw new AssertionError("boom");
            handled.add(u.updateId());
        }, new BotStatus(), 1);
        p.pollOnce(0);
        assertThat(p.awaitIdle(2000)).isTrue();
        assertThat(handled).containsExactly(2L);
        assertThat(calls.get()).isEqualTo(2);
        p.stop();
    }

    @Test
    void pollingError_propagates_forBackoff() {
        FakeGateway g = new FakeGateway();
        g.script.add(new org.springframework.web.client.RestClientException("getUpdates failed: Unauthorized"));
        TelegramPoller p = poller(g, u -> {}, new BotStatus(), 1);
        assertThatThrownBy(() -> p.pollOnce(5)).isInstanceOf(org.springframework.web.client.RestClientException.class);
        assertThat(g.offsets).containsExactly(5L);
        p.stop();
    }

    /** N-12: sekin foydalanuvchi poller'ni ham, boshqa foydalanuvchilarni ham to'xtatmaydi; tartib saqlanadi. */
    @Test
    void slowUser_doesNotBlockPollingOrOthers_perUserOrderKept() throws Exception {
        FakeGateway g = new FakeGateway();
        g.script.add(List.of(updFrom(100, 1), updFrom(101, 2)));
        g.script.add(List.of(updFrom(100, 1), updFrom(102, 2), updFrom(103, 1)));
        var perUser = new java.util.concurrent.ConcurrentHashMap<Long, List<Long>>();
        var release = new java.util.concurrent.CountDownLatch(1);
        TelegramPoller p = poller(g, u -> {
            long user = u.message().from().id();
            if (user == 1 && u.updateId() == 100) {
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            perUser.computeIfAbsent(user, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(u.updateId());
        }, new BotStatus(), 4);
        long o = p.pollOnce(0);
        assertThat(o).isEqualTo(100); // 100 hali ishlanmoqda
        o = p.pollOnce(o);            // poller kutmasdan keyingi partiyani oldi; 100 qayta navbatga qo'yilmadi
        long deadline = System.currentTimeMillis() + 2000;
        while (perUser.getOrDefault(2L, List.of()).size() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertThat(perUser.get(2L)).containsExactly(101L, 102L); // 2-foydalanuvchi sekin 1-ni kutmadi
        assertThat(perUser.get(1L)).isNull();
        assertThat(o).isEqualTo(100);
        release.countDown();
        assertThat(p.awaitIdle(2000)).isTrue();
        assertThat(perUser.get(1L)).containsExactly(100L, 103L);
        assertThat(p.pollOnce(o)).isEqualTo(104);
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
        TelegramPoller p = poller(g, u -> { throw new AssertionError("no updates"); }, st, 2);
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
        TelegramPoller p = poller(new FakeGateway(), u -> {}, new BotStatus(), 1);
        p.stop();
        assertThat(p.pollOnce(3)).isEqualTo(3);
    }

    /** LOW: processed hisoblagichi parallel worker'larda yo'qolmaydi. */
    @Test
    void botStatus_processedCounter_isAtomic() throws Exception {
        BotStatus st = new BotStatus();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) { ts[i] = new Thread(() -> { for (int k = 0; k < 10_000; k++) st.processed(); }); ts[i].start(); }
        for (Thread t : ts) t.join();
        assertThat(st.getProcessedUpdates()).isEqualTo(80_000);
    }
}
