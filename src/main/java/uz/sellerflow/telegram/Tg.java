package uz.sellerflow.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** Telegram Bot API'ning SellerFlow ishlatadigan minimal qismi. */
public final class Tg {
    private Tg() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") long updateId, Message message,
                         @JsonProperty("callback_query") CallbackQuery callbackQuery) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId, User from, Chat chat, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
        public boolean isPrivate() { return "private".equals(type); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id, @JsonProperty("is_bot") boolean isBot, @JsonProperty("first_name") String firstName,
                       String username, @JsonProperty("language_code") String languageCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, User from, Message message, String data) {}

    // ------------------------------------------------------------- markup

    public sealed interface Markup permits InlineKeyboard, ReplyKeyboard, RemoveKeyboard {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InlineButton(String text, @JsonProperty("callback_data") String callbackData, String url) {}

    public record InlineKeyboard(@JsonProperty("inline_keyboard") List<List<InlineButton>> rows) implements Markup {}

    public record KeyboardButton(String text) {}

    public record ReplyKeyboard(List<List<KeyboardButton>> keyboard,
                                @JsonProperty("resize_keyboard") boolean resize,
                                @JsonProperty("is_persistent") boolean persistent) implements Markup {}

    public record RemoveKeyboard(@JsonProperty("remove_keyboard") boolean remove) implements Markup {}

    /** Inline klaviatura quruvchi. callback_data 64 baytdan oshmasligi tekshiriladi. */
    public static final class Kb {
        private final List<List<InlineButton>> rows = new ArrayList<>();

        public static Kb create() { return new Kb(); }

        public Kb row(InlineButton... buttons) {
            if (buttons.length > 0) rows.add(List.of(buttons));
            return this;
        }

        public Kb rows(List<InlineButton> buttons, int perRow) {
            for (int i = 0; i < buttons.size(); i += perRow) {
                rows.add(List.copyOf(buttons.subList(i, Math.min(i + perRow, buttons.size()))));
            }
            return this;
        }

        public boolean isEmpty() { return rows.isEmpty(); }

        public InlineKeyboard build() { return new InlineKeyboard(List.copyOf(rows)); }

        public static InlineButton cb(String text, String data) {
            if (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64) {
                throw new IllegalArgumentException("callback_data too long: " + data);
            }
            return new InlineButton(text, data, null);
        }

        public static InlineButton url(String text, String url) {
            return new InlineButton(text, null, url);
        }
    }
}
