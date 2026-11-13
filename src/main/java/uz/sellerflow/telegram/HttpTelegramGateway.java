package uz.sellerflow.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.sellerflow.common.SellerFlowProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Bot API HTTP klienti. Token faqat muhit o'zgaruvchisidan; logga yozilmaydi.
 */
@Component
@ConditionalOnExpression("'${sellerflow.telegram.bot-token:}' != ''")
public class HttpTelegramGateway implements TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpTelegramGateway.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse<T>(boolean ok, T result, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpdatesResponse(boolean ok, List<Tg.Update> result, String description) {}

    /** M-11: bitta chiquvchi chaqiruv uchun ko'pi bilan shuncha urinish (429 / 5xx / tarmoq xatosi). */
    static final int MAX_ATTEMPTS = 3;
    /** 429 retry_after shundan uzun bo'lsa — kutib turmaymiz (poller oqimini bloklamaslik uchun). */
    static final long MAX_RETRY_AFTER_SECONDS = 10;
    static final long BASE_BACKOFF_MS = 500;

    /** getUpdates (long-poll) uchun — o'qish timeout'i polling'dan uzun. */
    private final RestClient client;
    /** LOW (N-12): chiquvchi chaqiruvlar uchun qisqa timeout — sekin Telegram worker'ni 45 s × 3 ushlab turmaydi. */
    private final RestClient sendClient;
    static final int SEND_READ_TIMEOUT_MS = 15_000;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private LongConsumer sleeper = HttpTelegramGateway::sleep;

    public HttpTelegramGateway(SellerFlowProperties props, ObjectMapper mapper) {
        this(props, mapper, new SimpleMeterRegistry());
    }

    @Autowired
    public HttpTelegramGateway(SellerFlowProperties props, ObjectMapper mapper, MeterRegistry meters) {
        this.mapper = mapper;
        this.meters = meters;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout((props.telegram().pollingTimeoutSeconds() + 15) * 1000);
        this.client = RestClient.builder()
                .baseUrl(props.telegram().apiBaseUrl() + "/bot" + props.telegram().botToken())
                .requestFactory(new BufferingClientHttpRequestFactory(rf)) // Content-Length bilan, chunked emas
                .build();
        SimpleClientHttpRequestFactory sf = new SimpleClientHttpRequestFactory();
        sf.setConnectTimeout(10_000);
        sf.setReadTimeout(SEND_READ_TIMEOUT_MS);
        this.sendClient = RestClient.builder()
                .baseUrl(props.telegram().apiBaseUrl() + "/bot" + props.telegram().botToken())
                .requestFactory(new BufferingClientHttpRequestFactory(sf))
                .build();
    }

    @Override
    public void sendMessage(long chatId, String html, Tg.Markup markup) {
        // M-14: 4096 belgidan uzun xabar bo'laklarga bo'linadi; tugmalar faqat oxirgi bo'lakda
        List<String> parts = uz.sellerflow.common.TextUtil.splitForTelegram(html, uz.sellerflow.common.TextUtil.TELEGRAM_LIMIT);
        for (int i = 0; i < parts.size(); i++) {
            sendOne(chatId, parts.get(i), i == parts.size() - 1 ? markup : null);
        }
    }

    private void sendOne(long chatId, String html, Tg.Markup markup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (markup != null) body.put("reply_markup", markup);
        post("sendMessage", body);
    }

    @Override
    public void clearInlineKeyboard(long chatId, long messageId) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", new Tg.InlineKeyboard(List.of()));
        post("editMessageReplyMarkup", body);
    }

    @Override
    public void answerCallback(String callbackId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackId);
        if (text != null) body.put("text", text);
        post("answerCallbackQuery", body);
    }

    @Override
    public List<Tg.Update> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> body = Map.of("offset", offset, "timeout", timeoutSeconds,
                "allowed_updates", List.of("message", "callback_query"));
        UpdatesResponse r = client.post().uri("/getUpdates").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(UpdatesResponse.class);
        if (r == null || !r.ok()) {
            throw new RestClientException("getUpdates failed: " + (r == null ? "null" : r.description()));
        }
        return r.result() == null ? List.of() : r.result();
    }

    public void deleteWebhook() {
        post("deleteWebhook", Map.of("drop_pending_updates", false));
    }

    public void setCommands(List<Map<String, String>> commands) {
        post("setMyCommands", Map.of("commands", commands));
    }

    /** Testlar uchun: kutishni almashtirish (millisekund). */
    public void setSleeper(LongConsumer sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * M-11: 429 da {@code parameters.retry_after} hurmat qilinadi (≤ {@value #MAX_RETRY_AFTER_SECONDS} s),
     * 5xx va tarmoq xatolarida eksponensial kutish bilan cheklangan qayta urinish; boshqa 4xx — qayta urinilmaydi.
     * Metrikalar: {@code telegram.api.calls{method,outcome}}, {@code telegram.api.latency{method}}.
     */
    private void post(String method, Object body) {
        for (int attempt = 1; ; attempt++) {
            long started = System.nanoTime();
            try {
                sendClient.post().uri("/" + method).contentType(MediaType.APPLICATION_JSON).body(body)
                        .retrieve().toBodilessEntity();
                record(method, "ok", started);
                return;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                long waitMs = -1;
                if (status == 429) {
                    long ra = retryAfterSeconds(e.getResponseBodyAsString());
                    if (ra <= MAX_RETRY_AFTER_SECONDS) waitMs = Math.max(ra, 1) * 1000;
                } else if (status >= 500) {
                    waitMs = BASE_BACKOFF_MS << (attempt - 1);
                }
                boolean retry = waitMs >= 0 && attempt < MAX_ATTEMPTS;
                record(method, retry ? "retry" : status == 429 ? "rate_limited" : "error", started);
                if (!retry) {
                    // Javob tanasida token yo'q; URL (token bilan) logga yozilmaydi
                    log.warn("Telegram {} failed: HTTP {} {}", method, status, e.getResponseBodyAsString());
                    return;
                }
                sleeper.accept(waitMs);
            } catch (RestClientException e) {
                // LOW: so'rov yetib borib, javobni kutishda timeout bo'lgan bo'lsa — sendMessage qayta yuborilmaydi
                // (aks holda foydalanuvchi bir xil xabarni ikki marta oladi). Ulanish xatolari — qayta urinadi.
                boolean delivered = isReadTimeout(e) && "sendMessage".equals(method);
                boolean retry = attempt < MAX_ATTEMPTS && !delivered;
                record(method, retry ? "retry" : "error", started);
                if (!retry) {
                    log.warn("Telegram {} failed: {}", method, e.getClass().getSimpleName());
                    return;
                }
                sleeper.accept(BASE_BACKOFF_MS << (attempt - 1));
            }
        }
    }

    static boolean isReadTimeout(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.net.SocketTimeoutException && c.getMessage() != null
                    && c.getMessage().toLowerCase(java.util.Locale.ROOT).contains("read")) return true;
        }
        return false;
    }

    private long retryAfterSeconds(String body) {
        try {
            return mapper.readTree(body).path("parameters").path("retry_after").asLong(1);
        } catch (Exception e) {
            return 1;
        }
    }

    private void record(String method, String outcome, long startedNanos) {
        meters.counter("telegram.api.calls", "method", method, "outcome", outcome).increment();
        meters.timer("telegram.api.latency", "method", method).record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
