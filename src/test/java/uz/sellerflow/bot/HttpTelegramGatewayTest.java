package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.sellerflow.common.SellerFlowProperties;
import uz.sellerflow.telegram.HttpTelegramGateway;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Bot API'ga yuboriladigan JSON formati to'g'riligini lokal HTTP server bilan tekshiradi. */
class HttpTelegramGatewayTest {

    private HttpServer server;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private HttpTelegramGateway gateway;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botTEST", ex -> {
            String method = ex.getRequestURI().getPath().replace("/botTEST/", "");
            bodies.put(method, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = method.equals("getUpdates")
                    ? "{\"ok\":true,\"result\":[{\"update_id\":10,\"message\":{\"message_id\":1,\"from\":{\"id\":7,\"is_bot\":false,\"first_name\":\"A\"},\"chat\":{\"id\":7,\"type\":\"private\"},\"text\":\"/start\",\"date\":1}},"
                    + "{\"update_id\":11,\"callback_query\":{\"id\":\"q\",\"from\":{\"id\":7,\"is_bot\":false,\"first_name\":\"A\"},\"message\":{\"message_id\":2,\"chat\":{\"id\":7,\"type\":\"private\"}},\"data\":\"w:1:C:back\"}}]}"
                    : "{\"ok\":true,\"result\":true}";
            byte[] b = resp.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        server.start();
        SellerFlowProperties props = new SellerFlowProperties(
                new SellerFlowProperties.Telegram("TEST", "http://127.0.0.1:" + server.getAddress().getPort(), true, 1),
                null, null, null, null);
        gateway = new HttpTelegramGateway(props, mapper);
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void sendMessage_inlineKeyboard_serializedAsBotApiExpects() throws Exception {
        gateway.sendMessage(7, "<b>Salom</b>", Kb.create().row(Kb.cb("💰 Foyda", "w:1:C:back"), Kb.url("Yordam", "https://t.me/x")).build());
        JsonNode body = mapper.readTree(bodies.get("sendMessage"));
        assertThat(body.path("chat_id").asLong()).isEqualTo(7);
        assertThat(body.path("parse_mode").asText()).isEqualTo("HTML");
        JsonNode row = body.path("reply_markup").path("inline_keyboard").get(0);
        assertThat(row.get(0).path("callback_data").asText()).isEqualTo("w:1:C:back");
        assertThat(row.get(0).has("url")).isFalse();
        assertThat(row.get(1).path("url").asText()).isEqualTo("https://t.me/x");
        assertThat(row.get(1).has("callback_data")).isFalse();
    }

    @Test
    void sendMessage_replyKeyboard_andRemove() throws Exception {
        gateway.sendMessage(7, "menu", new Tg.ReplyKeyboard(List.of(List.of(new Tg.KeyboardButton("A"))), true, true));
        JsonNode rk = mapper.readTree(bodies.get("sendMessage")).path("reply_markup");
        assertThat(rk.path("keyboard").get(0).get(0).path("text").asText()).isEqualTo("A");
        assertThat(rk.path("resize_keyboard").asBoolean()).isTrue();
        gateway.sendMessage(7, "x", new Tg.RemoveKeyboard(true));
        assertThat(mapper.readTree(bodies.get("sendMessage")).path("reply_markup").path("remove_keyboard").asBoolean()).isTrue();
    }

    @Test
    void getUpdates_parsesMessagesAndCallbacks() {
        List<Tg.Update> ups = gateway.getUpdates(0, 1);
        assertThat(ups).hasSize(2);
        assertThat(ups.get(0).message().text()).isEqualTo("/start");
        assertThat(ups.get(0).message().chat().isPrivate()).isTrue();
        assertThat(ups.get(1).callbackQuery().data()).isEqualTo("w:1:C:back");
    }
}
