package uz.sellerflow.bot;

import org.springframework.stereotype.Component;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.account.AccountService;
import uz.sellerflow.account.Business;
import uz.sellerflow.account.Consent;
import uz.sellerflow.common.Errors;
import uz.sellerflow.plan.PlanService;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ⚙️ Sozlamalar (SF-PRD-02.10 DRAFT, UX-DEC-37/38/39 "A" variantlari 05.11.2026 da default sifatida qabul qilingan).
 * Telefon maydoni V1.0 da yo'q (qaror: telefon so'ralmaydi).
 */
@Component
public class SettingsHandler {

    static final String SE = "SE";
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Tashkent"));

    private final AccountService accounts;
    private final PlanService plans;
    private final OnboardingHandler onboarding;
    private BotDispatcher dispatcher;

    public SettingsHandler(AccountService accounts, PlanService plans, OnboardingHandler onboarding) {
        this.accounts = accounts;
        this.plans = plans;
        this.onboarding = onboarding;
    }

    void bind(BotDispatcher d) { this.dispatcher = d; }

    public void showCenter(Ctx ctx) {
        Business b = ctx.business();
        Optional<Consent> consent = accounts.latestConsent(ctx.userId());
        String planName = Optional.ofNullable(plans.current(b.getPlanCode()).plan())
                .map(PlanService.Plan::getName).orElse(b.getPlanCode());
        String text = String.format(Texts.SETTINGS,
                Ctx.esc(b.businessNameOrDefault()), Ctx.esc(b.displayNameOrDefault()),
                b.getPrimaryChannel().label(), Ctx.esc(planName),
                consent.map(c -> "versiya " + c.getTermsVersion()).orElse("—"));
        ctx.send(text, Kb.create()
                .row(Kb.cb(Texts.BTN_S_BNAME, "s:bn"), Kb.cb(Texts.BTN_S_DNAME, "s:dn"))
                .row(Kb.cb(Texts.BTN_S_CHANNEL, "s:ch"), Kb.cb(Texts.BTN_S_TERMS, "s:terms"))
                .row(Kb.cb(Texts.BTN_TARIFFS_INLINE, "t:show"), Kb.cb("❓ Yordam", "f:list"))
                .row(Kb.cb(Texts.BTN_S_DELETE, "s:del1")));
    }

    public void onCallback(Ctx ctx, String[] p) {
        if ("se".equals(p[0])) {
            onFlowCallback(ctx, p);
            return;
        }
        String a = p.length > 1 ? p[1] : "";
        switch (a) {
            case "c" -> showCenter(ctx);
            case "bn" -> dispatcher.requestStart(ctx, "SEDIT:BN");
            case "dn" -> dispatcher.requestStart(ctx, "SEDIT:DN");
            case "ch" -> {
                List<Tg.InlineButton> bs = new ArrayList<>();
                for (SalesChannel c : SalesChannel.values()) bs.add(Kb.cb(c.label(), "s:chs:" + c.name()));
                ctx.send(String.format(Texts.CHANNEL_CHOOSE, ctx.business().getPrimaryChannel().label()),
                        Kb.create().rows(bs, 2).row(Kb.cb(Texts.BTN_BACK, "s:c")));
            }
            case "chs" -> {
                SalesChannel target = SalesChannel.valueOf(p[2]);
                if (target == ctx.business().getPrimaryChannel()) {
                    ctx.send(Texts.CHANNEL_SAME);
                    return;
                }
                ctx.send(String.format(Texts.CHANNEL_CONFIRM, ctx.business().getPrimaryChannel().label(), target.label()),
                        Kb.create().row(Kb.cb(Texts.BTN_CONFIRM, "s:chc:" + target.name()), Kb.cb(Texts.BTN_CANCEL, "s:c")));
            }
            case "chc" -> {
                ctx.clearSourceButtons();
                accounts.updatePrimaryChannel(ctx.businessId(), SalesChannel.valueOf(p[2]));
                refresh(ctx);
                ctx.send(Texts.SETTINGS_SAVED);
                showCenter(ctx);
            }
            case "terms" -> {
                Optional<Consent> c = accounts.latestConsent(ctx.userId());
                ctx.send(String.format(Texts.TERMS_INFO,
                        c.map(Consent::getTermsVersion).orElse("—"),
                        c.map(x -> FULL.format(x.getAcceptedAt())).orElse("—"),
                        Ctx.esc(onboarding.termsText())), Kb.create().row(Kb.cb(Texts.BTN_BACK, "s:c")));
            }
            case "del1" -> ctx.send(Texts.DELETE_STEP1, Kb.create()
                    .row(Kb.cb(Texts.BTN_DELETE_CONTINUE, "s:del2"), Kb.cb(Texts.BTN_CANCEL, "s:c")));
            case "del2" -> {
                ctx.clearSourceButtons();
                ctx.send(Texts.DELETE_STEP2, Kb.create()
                        .row(Kb.cb(Texts.BTN_DELETE_FINAL, "s:del3"), Kb.cb(Texts.BTN_CANCEL, "s:c")));
            }
            case "del3" -> {
                ctx.clearSourceButtons();
                accounts.requestDeletion(ctx.userId());
                ctx.session.clear();
                ctx.send(Texts.DELETE_DONE + "\n\n" + Texts.ACCOUNT_DELETION_REQUESTED, new Tg.RemoveKeyboard(true));
            }
            default -> ctx.send(Texts.STALE);
        }
    }

    private void refresh(Ctx ctx) {
        accounts.find(ctx.telegramUserId).ifPresent(a -> ctx.account = a);
    }

    // ================================================================= text edit flow

    void startEdit(Ctx ctx, String field) {
        ctx.session.start(Session.SETTINGS_EDIT, SE);
        ctx.session.put("field", field);
        render(ctx);
    }

    public void render(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        String prompt = "BN".equals(s.str("field")) ? Texts.ASK_S_BNAME : Texts.ASK_S_DNAME;
        ctx.send(prompt, Kb.create().row(
                Kb.cb(Texts.BTN_CLEAR, "se:" + rev + ":" + SE + ":clear"),
                Kb.cb(Texts.BTN_CANCEL, "se:" + rev + ":" + SE + ":cancel")));
    }

    public void onText(Ctx ctx, String text) {
        try {
            apply(ctx, text);
        } catch (Errors.Validation e) {
            ctx.send(BotDispatcher.validationMessage(e.code()));
        }
    }

    private void apply(Ctx ctx, String value) {
        Session s = ctx.session;
        if (value != null && value.isBlank()) {
            ctx.send(Texts.ERR_TEXT_EMPTY);
            return;
        }
        if ("BN".equals(s.str("field"))) accounts.updateBusinessName(ctx.businessId(), value);
        else accounts.updateDisplayName(ctx.businessId(), value);
        s.clear();
        refresh(ctx);
        ctx.send(Texts.SETTINGS_SAVED);
        showCenter(ctx);
    }

    private void onFlowCallback(Ctx ctx, String[] p) {
        Session s = ctx.session;
        if (p.length < 4 || !s.isFlow(Session.SETTINGS_EDIT) || !String.valueOf(s.revision()).equals(p[1])) {
            ctx.send(Texts.STALE);
            return;
        }
        ctx.clearSourceButtons();
        if ("clear".equals(p[3])) {
            apply(ctx, null);
        } else {
            s.clear();
            showCenter(ctx);
        }
    }
}
