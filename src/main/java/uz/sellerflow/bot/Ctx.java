package uz.sellerflow.bot;

import uz.sellerflow.account.AccountService.AccountContext;
import uz.sellerflow.account.Business;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.TelegramGateway;

import java.util.List;

/** Bitta update'ni qayta ishlash konteksti. */
public final class Ctx {

    final long chatId;
    final long telegramUserId;
    AccountContext account;
    final Session session;
    final TelegramGateway tg;
    /** Callback bosilgan xabar (bo'lsa) — tugmalarni tozalash uchun. */
    final Long sourceMessageId;

    Ctx(long chatId, long telegramUserId, AccountContext account, Session session, TelegramGateway tg,
        Long sourceMessageId) {
        this.chatId = chatId;
        this.telegramUserId = telegramUserId;
        this.account = account;
        this.session = session;
        this.tg = tg;
        this.sourceMessageId = sourceMessageId;
    }

    public Business business() { return account.business(); }

    public long businessId() { return account.business().getId(); }

    public long userId() { return account.user().getId(); }

    public void send(String html) { tg.sendMessage(chatId, html, null); }

    public void send(String html, Tg.Markup markup) { tg.sendMessage(chatId, html, markup); }

    public void send(String html, Tg.Kb kb) { tg.sendMessage(chatId, html, kb == null || kb.isEmpty() ? null : kb.build()); }

    public void sendMenu(String html) { tg.sendMessage(chatId, html, mainMenu()); }

    /** Bosilgan inline tugmalarni olib tashlash (best effort). */
    public void clearSourceButtons() {
        if (sourceMessageId != null) tg.clearInlineKeyboard(chatId, sourceMessageId);
    }

    /** Asosiy 6 tugmali Reply Keyboard (UX-NAV, UX-MENU). */
    public static Tg.ReplyKeyboard mainMenu() {
        return new Tg.ReplyKeyboard(List.of(
                List.of(new Tg.KeyboardButton(Texts.BTN_PROFIT), new Tg.KeyboardButton(Texts.BTN_PRICE)),
                List.of(new Tg.KeyboardButton(Texts.BTN_PRODUCTS), new Tg.KeyboardButton(Texts.BTN_HISTORY)),
                List.of(new Tg.KeyboardButton(Texts.BTN_SETTINGS), new Tg.KeyboardButton(Texts.BTN_TARIFFS))),
                true, true);
    }

    /** HTML uchun foydalanuvchi matnini ekranlash. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
