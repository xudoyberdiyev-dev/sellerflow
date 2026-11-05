package uz.sellerflow.bot;

import org.springframework.stereotype.Component;
import uz.sellerflow.account.AccountEntities.AccountStatus;
import uz.sellerflow.plan.PlanService;
import uz.sellerflow.plan.PlanService.PlanFeature;
import uz.sellerflow.plan.PlanService.PlanView;
import uz.sellerflow.support.SupportService;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;

import java.util.ArrayList;
import java.util.List;

/**
 * ❓ Yordam / FAQ (SF-PRD-02.08) va ⭐ Tariflar (SF-PRD-02.09).
 * Yordam biznes qiymatlarni o'zgartirmaydi va qoralama muddatini uzaytirmaydi (UX-HELP-005/008).
 */
@Component
public class InfoHandler {

    private final PlanService plans;
    private final SupportService support;
    private final OnboardingHandler onboarding;
    private BotDispatcher dispatcher;

    public InfoHandler(PlanService plans, SupportService support, OnboardingHandler onboarding) {
        this.plans = plans;
        this.support = support;
        this.onboarding = onboarding;
    }

    void bind(BotDispatcher d) { this.dispatcher = d; }

    // ================================================================= help

    public void showHelp(Ctx ctx, boolean contextual) {
        Integer contextFaq = contextual ? contextFaq(ctx.session) : null;
        StringBuilder text = new StringBuilder(Texts.HELP);
        if (contextFaq != null) text.append(Texts.HELP_CONTEXT);
        support.contact().ifPresent(c -> text.append("\n\n📞 Qo‘llab-quvvatlash: ").append(Ctx.esc(c.label())));
        Kb kb = Kb.create();
        if (contextFaq != null) kb.row(Kb.cb("💡 " + Texts.FAQ[contextFaq][0], "f:" + contextFaq));
        List<Tg.InlineButton> bs = new ArrayList<>();
        for (int i = 0; i < Texts.FAQ.length; i++) {
            if (contextFaq != null && i == contextFaq) continue;
            bs.add(Kb.cb(Texts.FAQ[i][0], "f:" + i));
        }
        kb.rows(bs, 1);
        addFooter(ctx, kb);
        ctx.send(text.toString(), kb);
    }

    private void addFooter(Ctx ctx, Kb kb) {
        support.contact().ifPresent(c -> kb.row(Kb.url("📞 " + c.label(), c.url())));
        if (ctx.session.hasFlow()) kb.row(Kb.cb(Texts.BTN_BACK_TO_FLOW, "f:back"));
    }

    /** Joriy Wizard qadamiga mos savol (kontekstli yordam, UX-DEC-32). */
    private static Integer contextFaq(Session s) {
        if (!s.hasFlow() || s.step() == null) return null;
        return switch (s.step()) {
            case CalcHandler.KT, CalcHandler.KV -> 3;
            case CalcHandler.X, CalcHandler.XT, CalcHandler.XV -> 2;
            case CalcHandler.G, CalcHandler.T -> 4;
            case CalcHandler.RS -> Session.PRICE.equals(s.flowType()) ? 5 : 1;
            case CalcHandler.R -> 6;
            case CalcHandler.C, CalcHandler.P -> 0;
            default -> null;
        };
    }

    public void onCallback(Ctx ctx, String[] p) {
        String a = p.length > 1 ? p[1] : "";
        if ("t".equals(p[0])) {
            showTariffs(ctx);
            return;
        }
        switch (a) {
            case "list" -> showHelp(ctx, true);
            case "back" -> {
                AccountStatus st = ctx.account.user().getStatus();
                if (st == AccountStatus.ONBOARDING && ctx.session.isFlow(Session.ONBOARDING)) onboarding.render(ctx);
                else if (st == AccountStatus.ACTIVE) dispatcher.resumeCurrent(ctx);
                else ctx.send(Texts.USE_MENU);
            }
            default -> {
                int i;
                try {
                    i = Integer.parseInt(a);
                } catch (NumberFormatException e) {
                    ctx.send(Texts.STALE);
                    return;
                }
                if (i < 0 || i >= Texts.FAQ.length) {
                    ctx.send(Texts.STALE);
                    return;
                }
                Kb kb = Kb.create().row(Kb.cb(Texts.BTN_FAQ_LIST, "f:list"));
                addFooter(ctx, kb);
                ctx.send("❓ <b>" + Texts.FAQ[i][0] + "</b>\n\n" + Texts.FAQ[i][1], kb);
            }
        }
    }

    // ================================================================= tariffs

    public void showTariffs(Ctx ctx) {
        PlanView v = plans.current(ctx.business().getPlanCode());
        if (v.plan() == null) {
            ctx.send(Texts.TARIFF_UNAVAILABLE);
            return;
        }
        StringBuilder feats = new StringBuilder();
        for (PlanFeature f : v.features()) {
            feats.append("• ").append(featureLabel(f.getFeature())).append(": ").append(limitLabel(f)).append("\n");
        }
        List<PlanService.Plan> published = plans.published();
        StringBuilder others = new StringBuilder();
        for (PlanService.Plan p : published) {
            if (p.getCode().equals(v.plan().getCode())) continue;
            others.append("• ").append(Ctx.esc(p.getName())).append("\n");
        }
        String footer = others.length() == 0 ? Texts.TARIFF_NO_PAID : "<b>Boshqa tariflar:</b>\n" + others;
        String desc = v.plan().getDescription() == null ? "" : Ctx.esc(v.plan().getDescription());
        ctx.send(String.format(Texts.TARIFF, Ctx.esc(v.plan().getName()), desc, feats.toString().stripTrailing(), footer),
                Kb.create().row(Kb.cb("❓ Savollar", "f:list"), Kb.cb(Texts.BTN_MAIN, "m")));
    }

    private static String featureLabel(String code) {
        return switch (code) {
            case "PROFIT_CALCULATION" -> "Foydani hisoblash";
            case "PRICE_CALCULATION" -> "Narxni aniqlash";
            case "PRODUCT_CATALOG" -> "Mahsulotlar katalogi";
            case "CALCULATION_HISTORY" -> "Hisoblashlar tarixi";
            default -> code;
        };
    }

    /** UNKNOWN hech qachon 0 yoki cheksiz deb ko'rsatilmaydi (UX-DEC-36). */
    private static String limitLabel(PlanFeature f) {
        return switch (f.getLimitType()) {
            case UNLIMITED -> "beta davrida cheklanmagan";
            case LIMIT_APPLIED -> f.getLimitValue() == null ? "limit ma’lumoti yo‘q" : f.getLimitValue() + " ta";
            case NOT_APPLICABLE -> "qo‘llanilmaydi";
            case UNKNOWN -> "ma’lumot olinmadi";
        };
    }
}
