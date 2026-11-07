package uz.sellerflow.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.account.AccountService;
import uz.sellerflow.account.AccountService.AccountContext;
import uz.sellerflow.common.Errors;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;
import uz.sellerflow.telegram.TelegramGateway;

import java.time.Instant;

/**
 * Har bir Telegram update uchun kirish nuqtasi: idempotentlik, private-only, akkaunt holati,
 * qoralama muddati, marshrutlash va «bitta faol jarayon» siyosati (SF-PRD-02.01, 02.03).
 */
@Component
public class BotDispatcher implements uz.sellerflow.telegram.UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(BotDispatcher.class);

    private final TelegramGateway tg;
    private final AccountService accounts;
    private final FlowStore flows;
    private final JdbcTemplate jdbc;
    private final OnboardingHandler onboarding;
    private final CalcHandler calc;
    private final ProductHandler products;
    private final HistoryHandler history;
    private final SettingsHandler settings;
    private final InfoHandler info;
    private final AccessHandler access;
    private final RateLimiter rateLimiter;
    private uz.sellerflow.account.ActivityTracker activity;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setActivityTracker(uz.sellerflow.account.ActivityTracker activity) {
        this.activity = activity;
    }

    public BotDispatcher(TelegramGateway tg, AccountService accounts, FlowStore flows, JdbcTemplate jdbc,
                         OnboardingHandler onboarding, CalcHandler calc, ProductHandler products,
                         HistoryHandler history, SettingsHandler settings, InfoHandler info,
                         AccessHandler access, RateLimiter rateLimiter) {
        this.tg = tg;
        this.accounts = accounts;
        this.flows = flows;
        this.jdbc = jdbc;
        this.onboarding = onboarding;
        this.calc = calc;
        this.products = products;
        this.history = history;
        this.settings = settings;
        this.info = info;
        this.access = access;
        this.rateLimiter = rateLimiter;
        calc.bind(this);
        products.bind(this);
        history.bind(this);
        settings.bind(this);
        info.bind(this);
    }

    // =================================================================== entry

    /**
     * M-10 (at-least-once): update_id oldin qayta ishlangan bo'lsa — o'tkazib yuboriladi; belgi faqat MUVAFFAQIYATLI
     * qayta ishlashdan keyin qo'yiladi. Baza uzilgan kabi vaqtinchalik xatolar ({@link uz.sellerflow.common.Infra})
     * yuqoriga uzatiladi — poller offset'ni surmaydi va update qayta keladi.
     */
    @Override
    public void handle(Tg.Update u) {
        Boolean seen = jdbc.queryForObject("select exists(select 1 from processed_updates where update_id = ?)",
                Boolean.class, u.updateId());
        if (Boolean.TRUE.equals(seen)) {
            io.micrometer.core.instrument.Metrics.counter("sellerflow.updates", "kind", "duplicate").increment();
            return;
        }
        io.micrometer.core.instrument.Metrics.counter("sellerflow.updates",
                "kind", u.callbackQuery() != null ? "callback" : u.message() != null ? "message" : "other").increment();

        if (u.callbackQuery() != null) {
            handleCallback(u.callbackQuery());
        } else if (u.message() != null && u.message().from() != null && !u.message().from().isBot()) {
            handleMessage(u.message());
        }
        jdbc.update("insert into processed_updates(update_id) values (?) on conflict do nothing", u.updateId());
    }

    private void handleMessage(Tg.Message m) {
        long chatId = m.chat().id();
        if (!m.chat().isPrivate()) {
            // CR-CHAT-01: guruhda biznes oqimi yo'q, faqat umumiy yo'riqnoma
            if (m.text() != null && m.text().startsWith("/")) tg.sendMessage(chatId, Texts.GROUP_CHAT, null);
            return;
        }
        if (!allowed(chatId, m.from().id())) return;
        String text = m.text() == null ? "" : m.text().strip();
        Ctx ctx = context(chatId, m.from().id(), null);
        process(ctx, () -> onText(ctx, text));
    }

    private void handleCallback(Tg.CallbackQuery q) {
        if (q.message() == null || q.message().chat() == null || !q.message().chat().isPrivate()) {
            tg.answerCallback(q.id(), null);
            return;
        }
        String data = q.data() == null ? "" : q.data();
        if (!allowed(q.message().chat().id(), q.from().id())) {
            tg.answerCallback(q.id(), null);
            return;
        }
        Ctx ctx = context(q.message().chat().id(), q.from().id(), q.message().messageId());
        tg.answerCallback(q.id(), null);
        process(ctx, () -> onCallback(ctx, data.split(":")));
    }

    /** Spam himoyasi: limitdan oshsa update tashlanadi, oyna boshida bir marta ogohlantiriladi. */
    private boolean allowed(long chatId, long telegramUserId) {
        RateLimiter.Verdict v = rateLimiter.check(telegramUserId);
        if (v == RateLimiter.Verdict.WARN) tg.sendMessage(chatId, Texts.RATE_LIMITED, null);
        return v == RateLimiter.Verdict.OK;
    }

    private Ctx context(long chatId, long telegramUserId, Long sourceMessageId) {
        AccountContext account = accounts.findOrCreate(telegramUserId);
        if (activity != null) activity.touch(account.user().getId());
        Session session = flows.load(account.user().getId());
        return new Ctx(chatId, telegramUserId, account, session, tg, sourceMessageId);
    }

    private void process(Ctx ctx, Runnable action) {
        if (ctx.session.isExpired(Instant.now())) {
            ctx.session.clear();
            ctx.send(Texts.DRAFT_EXPIRED);
        }
        try {
            action.run();
            flows.save(ctx.session);
        } catch (Errors.NotFound e) {
            ctx.send(Texts.NOT_FOUND);
            flows.save(ctx.session);
        } catch (Errors.Conflict e) {
            ctx.send(Texts.ERR_CONFLICT);
            flows.save(ctx.session);
        } catch (Errors.Validation e) {
            ctx.send(validationMessage(e.code()));
            flows.save(ctx.session);
        } catch (Errors.Forbidden e) {
            ctx.send(Texts.FEATURE_LIMITED);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // Soxta/eskirgan callback data yoki buzilgan kiritish — dastur xatosi emas, stacktrace'siz WARN
            log.warn("Malformed input for user {}: {}", ctx.account.user().getId(), e.toString());
            io.micrometer.core.instrument.Metrics.counter("sellerflow.update.errors", "type", "malformed").increment();
            ctx.send(Texts.GENERIC_ERROR);
        } catch (RuntimeException e) {
            if (uz.sellerflow.common.Infra.isTransient(e)) {
                io.micrometer.core.instrument.Metrics.counter("sellerflow.update.errors", "type", "transient").increment();
                throw e; // M-10: update qayta ishlanadi
            }
            io.micrometer.core.instrument.Metrics.counter("sellerflow.update.errors", "type", "unexpected").increment();
            log.error("Bot handling error for user {}", ctx.account.user().getId(), e);
            ctx.send(Texts.GENERIC_ERROR);
        }
    }

    static String validationMessage(String code) {
        return switch (code) {
            case "SKU_TAKEN" -> Texts.ERR_SKU_TAKEN;
            case "SKU_FORMAT" -> Texts.ERR_SKU_FORMAT;
            case "PRODUCT_NAME_LENGTH" -> String.format(Texts.ERR_TEXT_LENGTH, 120);
            case "BUSINESS_NAME_LENGTH" -> String.format(Texts.ERR_TEXT_LENGTH, 80);
            case "DISPLAY_NAME_LENGTH" -> String.format(Texts.ERR_TEXT_LENGTH, 60);
            case "PRODUCT_NAME_REQUIRED" -> Texts.ERR_TEXT_EMPTY;
            case "VALUE_TOO_LARGE" -> Texts.ERR_TOO_LARGE;
            case "PLANNED_PRICE_NOT_POSITIVE" -> Texts.ERR_PRICE_POSITIVE;
            default -> Texts.GENERIC_ERROR;
        };
    }

    // =================================================================== text

    private void onText(Ctx ctx, String text) {
        AccountStatus status = ctx.account.user().getStatus();
        String cmd = command(text);

        if (status == AccountStatus.ONBOARDING || ctx.business() == null) {
            if ("/help".equals(cmd)) {
                info.showHelp(ctx, false);
                return;
            }
            if ("/delete".equals(cmd) && status == AccountStatus.ONBOARDING) {
                onboarding.askWipe(ctx); // M-26
                return;
            }
            onboarding.onText(ctx, text, cmd);
            return;
        }
        if (status != AccountStatus.ACTIVE) {
            restricted(ctx, status, "/help".equals(cmd));
            return;
        }
        if (!accounts.hasCurrentConsent(ctx.userId())) {
            if ("/help".equals(cmd)) info.showHelp(ctx, false);
            else showReconsent(ctx);
            return;
        }

        if (cmd != null) {
            switch (cmd) {
                case "/start", "/menu" -> {
                    ctx.session.pause();
                    ctx.session.uiMode(null);
                    ctx.sendMenu(Texts.MAIN_MENU);
                }
                case "/cancel" -> {
                    ctx.session.uiMode(null);
                    if (ctx.session.hasFlow()) {
                        ctx.session.clear();
                        ctx.sendMenu(Texts.CANCELLED);
                    } else {
                        ctx.sendMenu(Texts.NOTHING_TO_CANCEL);
                    }
                }
                case "/help" -> info.showHelp(ctx, true);
                case "/settings" -> {
                    ctx.session.pause();
                    ctx.session.uiMode(null);
                    settings.showCenter(ctx);
                }
                default -> ctx.sendMenu(Texts.USE_MENU);
            }
            return;
        }

        switch (text) {
            case Texts.BTN_PROFIT -> { ctx.session.uiMode(null); calc.enter(ctx, Session.PROFIT); return; }
            case Texts.BTN_PRICE -> { ctx.session.uiMode(null); calc.enter(ctx, Session.PRICE); return; }
            case Texts.BTN_PRODUCTS -> { pauseForView(ctx); products.showList(ctx, false, 0); return; }
            case Texts.BTN_HISTORY -> { pauseForView(ctx); history.showFresh(ctx); return; }
            case Texts.BTN_SETTINGS -> { pauseForView(ctx); settings.showCenter(ctx); return; }
            case Texts.BTN_TARIFFS -> { pauseForView(ctx); info.showTariffs(ctx); return; }
            default -> { }
        }

        // Qidiruv kabi yengil kiritish rejimlari faol qoralamani buzmaydi
        String mode = ctx.session.uiMode();
        if (mode != null) {
            ctx.session.uiMode(null);
            switch (mode) {
                case ProductHandler.MODE_SEARCH -> { products.search(ctx, text); return; }
                case HistoryHandler.MODE_SEARCH -> { history.search(ctx, text); return; }
                case HistoryHandler.MODE_RANGE -> { history.range(ctx, text); return; }
                default -> { }
            }
        }

        Session s = ctx.session;
        if (s.hasFlow() && !s.isPaused()) {
            switch (s.flowType()) {
                case Session.PROFIT, Session.PRICE -> calc.onText(ctx, text);
                case Session.PRODUCT_CREATE, Session.PRODUCT_EDIT -> products.onText(ctx, text);
                case Session.SETTINGS_EDIT -> settings.onText(ctx, text);
                default -> ctx.sendMenu(Texts.USE_MENU);
            }
            return;
        }
        if (s.hasFlow() && s.hasUnsavedData()) {
            ctx.send(Texts.USE_MENU_WITH_DRAFT, Kb.create().row(Kb.cb(Texts.BTN_CONTINUE, "rs:" + s.revision() + ":c")));
        } else {
            ctx.sendMenu(Texts.USE_MENU);
        }
    }

    private void pauseForView(Ctx ctx) {
        ctx.session.uiMode(null);
        ctx.session.pause();
    }

    private void restricted(Ctx ctx, AccountStatus status, boolean helpRequested) {
        if (helpRequested) {
            info.showHelp(ctx, false);
            return;
        }
        String msg = switch (status) {
            case SUSPENDED -> Texts.ACCOUNT_SUSPENDED;
            case DELETION_REQUESTED -> Texts.ACCOUNT_DELETION_REQUESTED;
            default -> Texts.ACCOUNT_CLOSED;
        };
        if (status == AccountStatus.CLOSED) {
            // M-24: yopilgan akkaunt egasi yangidan boshlay oladi
            ctx.send(msg, Kb.create().row(Kb.cb(Texts.BTN_REOPEN, "rn:x")).build());
            return;
        }
        ctx.send(msg, new Tg.RemoveKeyboard(true));
        if (status == AccountStatus.DELETION_REQUESTED) {
            // M-25: bekor qilish support'ga bog'liq emas
            ctx.send(Texts.DELETION_CANCEL_PROMPT, Kb.create().row(Kb.cb(Texts.BTN_DELETION_CANCEL, "dc:x")).build());
        }
    }

    private static String command(String text) {
        if (!text.startsWith("/")) return null;
        String c = text.split("\\s+")[0];
        int at = c.indexOf('@');
        return (at > 0 ? c.substring(0, at) : c).toLowerCase();
    }

    // =================================================================== callbacks

    private void onCallback(Ctx ctx, String[] p) {
        AccountStatus status = ctx.account.user().getStatus();
        String prefix = p[0];

        if (status == AccountStatus.ONBOARDING || ctx.business() == null) {
            if ("o".equals(prefix)) onboarding.onCallback(ctx, p);
            else if ("f".equals(prefix)) info.onCallback(ctx, p);
            else ctx.send(Texts.STALE);
            return;
        }
        if (status == AccountStatus.DELETION_REQUESTED && "dc".equals(prefix)) {
            ctx.clearSourceButtons();
            accounts.cancelDeletion(ctx.userId());
            ctx.sendMenu(Texts.DELETION_CANCELLED);
            return;
        }
        if (status == AccountStatus.CLOSED && "rn".equals(prefix)) {
            ctx.clearSourceButtons();
            accounts.reopenClosed(ctx.telegramUserId);
            Ctx fresh = context(ctx.chatId, ctx.telegramUserId, null);
            onboarding.onText(fresh, "/start", "/start");
            flows.save(fresh.session);
            return;
        }
        if (status != AccountStatus.ACTIVE) {
            if ("f".equals(prefix)) info.onCallback(ctx, p);
            else if ("g".equals(prefix) && p.length > 2 && "r".equals(p[2]) && ctx.business() != null) access.onCallback(ctx, p);
            else restricted(ctx, status, false);
            return;
        }
        if ("rc".equals(prefix)) {
            onReconsent(ctx, p);
            return;
        }
        boolean deletionPath = "s".equals(prefix) && p.length > 1 && p[1].startsWith("del");
        if (!accounts.hasCurrentConsent(ctx.userId()) && !"f".equals(prefix) && !"g".equals(prefix) && !deletionPath) {
            showReconsent(ctx);
            return;
        }

        switch (prefix) {
            case "m" -> { ctx.session.pause(); ctx.sendMenu(Texts.MAIN_MENU); }
            case "w" -> calc.onCallback(ctx, p);
            case "p", "pw" -> products.onCallback(ctx, p);
            case "h" -> history.onCallback(ctx, p);
            case "s", "se" -> settings.onCallback(ctx, p);
            case "f", "t" -> info.onCallback(ctx, p);
            case "sw" -> onSwitch(ctx, p);
            case "rs" -> onResume(ctx, p);
            case "g" -> access.onCallback(ctx, p);
            case "o" -> ctx.sendMenu(Texts.MAIN_MENU);
            default -> ctx.send(Texts.STALE);
        }
    }

    // =================================================================== re-consent

    /** Xizmat shartlari versiyasi o'zgargan: biznes amallari yangi aniq roziliksiz bajarilmaydi (PRD-01 5.4). */
    private void showReconsent(Ctx ctx) {
        String v = accounts.currentTermsVersion();
        ctx.send(String.format(Texts.RECONSENT, Ctx.esc(v), Ctx.esc(onboarding.termsText())), Kb.create()
                .row(Kb.cb(Texts.BTN_AGREE, "rc:a:" + v), Kb.cb(Texts.BTN_DECLINE, "rc:d")));
    }

    private void onReconsent(Ctx ctx, String[] p) {
        ctx.clearSourceButtons();
        if (p.length > 2 && "a".equals(p[1])) {
            // Faqat foydalanuvchiga ko'rsatilgan aynan shu versiya qabul qilinadi
            if (!accounts.currentTermsVersion().equals(p[2])) {
                showReconsent(ctx);
                return;
            }
            accounts.recordConsent(ctx.userId());
            ctx.sendMenu(Texts.RECONSENT_DONE);
        } else if (p.length > 1 && "d".equals(p[1])) {
            ctx.send(Texts.RECONSENT_DECLINED, Kb.create()
                    .row(Kb.cb(Texts.BTN_TERMS_AGAIN, "rc:show"))
                    .row(Kb.cb(Texts.BTN_S_DELETE, "s:del1")));
        } else {
            showReconsent(ctx);
        }
    }

    // =================================================================== single active flow policy

    /**
     * Yangi kiritish jarayonini boshlash so'rovi. Saqlanmagan ma'lumot bo'lsa — tasdiq (UX-DEC-17),
     * aks holda darhol bajariladi. target — qayta bajariladigan deskriptor (masalan "PROFIT", "PPRICE:12").
     */
    public void requestStart(Ctx ctx, String target) {
        Session s = ctx.session;
        if (s.hasUnsavedData()) {
            s.ui().put("pendingSwitch", target);
            ctx.send(String.format(Texts.SWITCH_CONFIRM, flowTitle(s.flowType())), Kb.create()
                    .row(Kb.cb(Texts.BTN_SWITCH_YES, "sw:" + s.revision() + ":y"))
                    .row(Kb.cb(Texts.BTN_SWITCH_NO, "sw:" + s.revision() + ":n")));
            return;
        }
        execute(ctx, target);
    }

    private void onSwitch(Ctx ctx, String[] p) {
        Session s = ctx.session;
        String target = s.ui().path("pendingSwitch").asText(null);
        if (p.length < 3 || target == null || !String.valueOf(s.revision()).equals(p[1])) {
            ctx.send(Texts.STALE);
            return;
        }
        ctx.clearSourceButtons();
        s.ui().remove("pendingSwitch");
        if ("y".equals(p[2])) {
            execute(ctx, target);
        } else {
            resumeCurrent(ctx);
        }
    }

    private void onResume(Ctx ctx, String[] p) {
        Session s = ctx.session;
        if (p.length < 3 || !String.valueOf(s.revision()).equals(p[1]) || !s.hasFlow()) {
            ctx.send(Texts.STALE);
            return;
        }
        ctx.clearSourceButtons();
        if ("c".equals(p[2])) {
            resumeCurrent(ctx);
        } else {
            requestStart(ctx, s.flowType());
        }
    }

    /** Joriy (to'xtatilgan) jarayonni davom ettirish: hozirgi qadamni qayta ko'rsatish. */
    public void resumeCurrent(Ctx ctx) {
        Session s = ctx.session;
        if (!s.hasFlow()) {
            ctx.sendMenu(Texts.MAIN_MENU);
            return;
        }
        s.resume();
        switch (s.flowType()) {
            case Session.PROFIT, Session.PRICE -> calc.render(ctx);
            case Session.PRODUCT_CREATE, Session.PRODUCT_EDIT -> products.render(ctx);
            case Session.SETTINGS_EDIT -> settings.render(ctx);
            default -> ctx.sendMenu(Texts.MAIN_MENU);
        }
    }

    private void execute(Ctx ctx, String target) {
        String[] t = target.split(":");
        switch (t[0]) {
            case Session.PROFIT, Session.PRICE -> calc.startNew(ctx, t[0]);
            case "TOPROFIT" -> calc.startProfitFromPrice(ctx);
            case "PPROFIT" -> calc.startFromProduct(ctx, Session.PROFIT, Long.parseLong(t[1]));
            case "PPRICE" -> calc.startFromProduct(ctx, Session.PRICE, Long.parseLong(t[1]));
            case "RECALC" -> history.startRecalc(ctx, Long.parseLong(t[1]), t.length > 2 && "c".equals(t[2]));
            case "PCREATE" -> products.startCreate(ctx);
            case "PEDIT" -> products.startEdit(ctx, Long.parseLong(t[1]), Integer.parseInt(t[2]), t[3]);
            case "SEDIT" -> settings.startEdit(ctx, t[1]);
            default -> ctx.sendMenu(Texts.MAIN_MENU);
        }
    }

    static String flowTitle(String flowType) {
        if (flowType == null) return "";
        return switch (flowType) {
            case Session.PROFIT -> "Foydani hisoblash";
            case Session.PRICE -> "Narxni aniqlash";
            case Session.PRODUCT_CREATE -> "Yangi mahsulot";
            case Session.PRODUCT_EDIT -> "Mahsulotni tahrirlash";
            case Session.SETTINGS_EDIT -> "Sozlamalarni tahrirlash";
            default -> flowType;
        };
    }
}
