package uz.sellerflow.telegram;

/** Bitta Telegram update'ni qayta ishlovchi (BotDispatcher). Poller testlarida soxtasi ishlatiladi. */
@FunctionalInterface
public interface UpdateHandler {
    void handle(Tg.Update update);
}
