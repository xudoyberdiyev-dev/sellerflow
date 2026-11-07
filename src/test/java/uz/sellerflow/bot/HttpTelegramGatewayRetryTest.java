package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.sellerflow.common.SellerFlowProperties;
import uz.sellerflow.telegram.HttpTelegramGateway;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** M-11: chiquvchi Telegram chaqiruvlari — 429 retry_after, 5xx retry, metrikalar. */
class HttpTelegramGatewayRetryTest {

    record Resp(int status, String body) {}

    private HttpServer server;
    private final ConcurrentLinkedDeque<Resp> script = new ConcurrentLinkedDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private HttpTelegramGateway gateway;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botTEST", ex -> {
            calls.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            Resp r = script.isEmpty() ? new Resp(200, "{\"ok\":true,\"result\":true}") : script.poll();
            byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        server.start();
        SellerFlowProperties props = new SellerFlowProperties(
                new SellerFlowProperties.Telegram("TEST", "http://127.0.0.1:" + server.getAddress().getPort(), true, 1),
                null, null, null, null, null, null);
        gateway = new HttpTelegramGateway(props, new ObjectMapper(), meters);
        gateway.setSleeper(sleeps::add);
    }

    @AfterEach
    void stop() { server.stop(0); }

    private double count(String outcome) {
        var c = meters.find("telegram.api.calls").tag("method", "sendMessage").tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void rateLimited_respectsRetryAfter_thenSucceeds() {
        script.add(new Resp(429, "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 3\",\"parameters\":{\"retry_after\":3}}"));
        gateway.sendMessage(7, "x", null);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(sleeps).containsExactly(3000L);
        assertThat(count("retry")).isEqualTo(1);
        assertThat(count("ok")).isEqualTo(1);
    }

    @Test
    void longRetryAfter_isNotWaited() {
        script.add(new Resp(429, "{\"ok\":false,\"parameters\":{\"retry_after\":120}}"));
        gateway.sendMessage(7, "x", null);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
        assertThat(count("rate_limited")).isEqualTo(1);
    }

    @Test
    void serverErrors_retriedWithExponentialBackoff_limited() {
        for (int i = 0; i < 5; i++) script.add(new Resp(502, "{\"ok\":false}"));
        gateway.sendMessage(7, "x", null);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(sleeps).containsExactly(500L, 1000L);
        assertThat(count("error")).isEqualTo(1);
    }

    /** M-14: 4096 dan uzun xabar bo'laklarga bo'linadi. */
    @Test
    void longMessage_isSplit() {
        String text = ("<b>qator</b> " + "x".repeat(90) + "\n").repeat(100); // ~10 000 belgi
        gateway.sendMessage(7, text, null);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void clientError_notRetried() {
        script.add(new Resp(400, "{\"ok\":false,\"description\":\"Bad Request: chat not found\"}"));
        gateway.sendMessage(7, "x", null);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
        assertThat(count("error")).isEqualTo(1);
    }
}
