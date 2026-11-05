package uz.sellerflow.bot;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.account.AccountService;
import uz.sellerflow.account.AccountService.AccountContext;
import uz.sellerflow.account.AccountService.ProfileRules;
import uz.sellerflow.account.Business;
import uz.sellerflow.common.Errors;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * /start va onboarding (SF-PRD-02.02): welcome (model B) -> consent -> kanal -> ixtiyoriy profil -> review
 * -> explicit faollashtirish -> Free -> 6 tugmali menyu. Telefon so'ralmaydi (05.11.2026 qarori).
 * Qadamlar: W, CONS, CHN, BN, DN, REV. Callback: o:{rev}:{action}[:arg].
 */
@Component
public class OnboardingHandler {

    static final String W = "W", CONS = "CONS", CHN = "CHN", BN = "BN", DN = "DN", REV = "REV";

    private final AccountService accounts;
    private final String termsText;

    public OnboardingHandler(AccountService accounts) throws IOException {
        this.accounts = accounts;
        this.termsText = new String(new ClassPathResource("bot/terms_uz.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).strip();
    }

    public String termsText() { return termsText; }

    public void onText(Ctx ctx, String text, String cmd) {
        Session s = ctx.session;
        if (!s.isFlow(Session.ONBOARDING)) {
            s.start(Session.ONBOARDING, W);
            render(ctx);
            return;
        }
        if ("/start".equals(cmd)) {
            render(ctx); // tegishli bosqichdan davom ettirish
            return;
        }
        switch (s.step()) {
            case BN -> {
                String v = validated(ctx, text, true);
                if (v == null) return;
                s.put("bn", v);
                s.step(s.data().path("fromReview").asBoolean(false) ? REV : DN);
                render(ctx);
            }
            case DN -> {
                String v = validated(ctx, text, false);
                if (v == null) return;
                s.put("dn", v);
                s.step(REV);
                render(ctx);
            }
            default -> {
                ctx.send(Texts.EXPECT_BUTTON);
                render(ctx);
            }
        }
    }

    private String validated(Ctx ctx, String text, boolean business) {
        try {
            String v = business ? ProfileRules.normalizeBusinessName(text) : ProfileRules.normalizeDisplayName(text);
            if (v == null) {
                ctx.send(Texts.ERR_TEXT_EMPTY);
                return null;
            }
            return v;
        } catch (Errors.Validation e) {
            ctx.send(BotDispatcher.validationMessage(e.code()));
            return null;
        }
    }

    public void render(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        switch (s.step()) {
            case W -> ctx.send(Texts.WELCOME, Kb.create()
                    .row(b(rev, Texts.BTN_START, "start"))
                    .row(b(rev, Texts.BTN_ABOUT, "about")));
            case CONS -> ctx.send(String.format(Texts.CONSENT, accounts.currentTermsVersion(), Ctx.esc(termsText)),
                    Kb.create().row(b(rev, Texts.BTN_AGREE, "agree"), b(rev, Texts.BTN_DECLINE, "decline")));
            case CHN -> {
                List<Tg.InlineButton> bs = new ArrayList<>();
                for (SalesChannel c : SalesChannel.values()) bs.add(b(rev, c.label(), "ch:" + c.name()));
                ctx.send(Texts.ASK_CHANNEL, Kb.create().rows(bs, 2));
            }
            case BN -> ctx.send(Texts.ASK_BUSINESS_NAME, Kb.create().row(b(rev, Texts.BTN_SKIP, "skip")));
            case DN -> ctx.send(Texts.ASK_DISPLAY_NAME, Kb.create().row(b(rev, Texts.BTN_SKIP, "skip")));
            case REV -> ctx.send(String.format(Texts.ONB_REVIEW,
                            CalcModel.channelLabel(s.str("ch")),
                            Ctx.esc(s.str("bn") == null ? Business.DEFAULT_BUSINESS_NAME : s.str("bn")),
                            Ctx.esc(s.str("dn") == null ? Business.DEFAULT_DISPLAY_NAME : s.str("dn"))),
                    Kb.create().row(b(rev, Texts.BTN_ACTIVATE, "act"))
                            .row(b(rev, Texts.BTN_EDIT_CHANNEL, "e:CHN"), b(rev, Texts.BTN_EDIT_BNAME, "e:BN"),
                                    b(rev, Texts.BTN_EDIT_DNAME, "e:DN")));
            default -> {
                s.step(W);
                render(ctx);
            }
        }
    }

    public void onCallback(Ctx ctx, String[] p) {
        Session s = ctx.session;
        if (!s.isFlow(Session.ONBOARDING) || p.length < 3 || !String.valueOf(s.revision()).equals(p[1])) {
            if (!s.isFlow(Session.ONBOARDING)) s.start(Session.ONBOARDING, W);
            ctx.send(Texts.STALE);
            render(ctx);
            return;
        }
        String action = p[2];
        String arg = p.length > 3 ? p[3] : null;
        switch (action) {
            case "about" -> {
                ctx.send(Texts.ABOUT, Kb.create().row(b(s.revision(), Texts.BTN_START, "start")));
                return;
            }
            case "start" -> s.step(CONS);
            case "agree" -> {
                accounts.recordConsent(ctx.account.user().getId());
                s.step(s.str("ch") == null ? CHN : REV);
            }
            case "decline" -> {
                // Rad etilsa ACTIVE bo'lmaydi; rozilik o'ylab topilmaydi
                ctx.send(Texts.CONSENT_DECLINED, Kb.create().row(b(s.revision(), Texts.BTN_TERMS_AGAIN, "start")));
                return;
            }
            case "ch" -> {
                s.put("ch", SalesChannel.valueOf(arg).name());
                s.step(s.data().path("fromReview").asBoolean(false) ? REV : BN);
            }
            case "skip" -> {
                if (BN.equals(s.step())) {
                    s.data().remove("bn");
                    s.step(s.data().path("fromReview").asBoolean(false) ? REV : DN);
                } else {
                    s.data().remove("dn");
                    s.step(REV);
                }
            }
            case "e" -> {
                s.data().put("fromReview", true);
                s.step(arg);
            }
            case "act" -> {
                activate(ctx);
                return;
            }
            default -> { }
        }
        ctx.clearSourceButtons();
        render(ctx);
    }

    private void activate(Ctx ctx) {
        Session s = ctx.session;
        long userId = ctx.account.user().getId();
        if (!accounts.hasCurrentConsent(userId)) {
            s.step(CONS);
            render(ctx);
            return;
        }
        if (s.str("ch") == null) {
            s.step(CHN);
            render(ctx);
            return;
        }
        AccountContext activated = accounts.activate(userId, SalesChannel.valueOf(s.str("ch")), s.str("bn"), s.str("dn"));
        ctx.account = activated;
        s.clear();
        ctx.clearSourceButtons();
        ctx.sendMenu(Texts.ACTIVATED);
    }

    private static Tg.InlineButton b(int rev, String text, String action) {
        return Kb.cb(text, "o:" + rev + ":" + action);
    }
}
