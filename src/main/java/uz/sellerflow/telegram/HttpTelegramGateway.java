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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final RestClient client;

    public HttpTelegramGateway(SellerFlowProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout((props.telegram().pollingTimeoutSeconds() + 15) * 1000);
        this.client = RestClient.builder()
                .baseUrl(props.telegram().apiBaseUrl() + "/bot" + props.telegram().botToken())
                .requestFactory(new BufferingClientHttpRequestFactory(rf)) // Content-Length bilan, chunked emas
                .build();
    }

    @Override
    public void sendMessage(long chatId, String html, Tg.Markup markup) {
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

    private void post(String method, Object body) {
        try {
            client.post().uri("/" + method).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Javob tanasida token yo'q; URL (token bilan) logga yozilmaydi
            log.warn("Telegram {} failed: HTTP {} {}", method, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("Telegram {} failed: {}", method, e.getClass().getSimpleName());
        }
    }
}
