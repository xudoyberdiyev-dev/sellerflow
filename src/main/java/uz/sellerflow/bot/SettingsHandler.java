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

    private uz.sellerflow.access.AccessGrantService grants;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGrants(uz.sellerflow.access.AccessGrantService grants) { this.grants = grants; }

    private uz.sellerflow.channel.ChannelRateService rates;
    private uz.sellerflow.export.DataExportService exporter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setExporter(uz.sellerflow.export.DataExportService exporter) { this.exporter = exporter; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRates(uz.sellerflow.channel.ChannelRateService rates) { this.rates = rates; }

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
                .row(Kb.cb(Texts.BTN_S_RATES, "s:cr"), Kb.cb(Texts.BTN_S_MIN_MARGIN, "s:mm"))
                .row(Kb.cb(Texts.BTN_S_EXPORT, "s:exp"))
                .row(Kb.cb(Texts.BTN_S_ACCESS, "s:acc"))
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
            case "mm" -> dispatcher.requestStart(ctx, "SEDIT:MM");
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
            case "acc" -> showAccess(ctx);
            case "cr" -> showRates(ctx);
            case "exp" -> exportData(ctx);
            case "crd" -> {
                ctx.clearSourceButtons();
                if (rates != null && p.length > 2) {
                    try {
                        SalesChannel c = SalesChannel.valueOf(p[2]);
                        if (rates.delete(ctx.businessId(), c)) ctx.send(String.format(Texts.RATES_DELETED, c.label()));
                    } catch (IllegalArgumentException ignored) {
                        // eski/buzilgan tugma
                    }
                }
                showRates(ctx);
            }
            case "del1" -> ctx.send(String.format(Texts.DELETE_STEP1,
                    accounts.purgeEnabled() ? Texts.DELETE_DATA_PURGED : Texts.DELETE_DATA_RETAINED), Kb.create()
                    .row(Kb.cb(Texts.BTN_DELETE_CONTINUE, "s:del2"), Kb.cb(Texts.BTN_CANCEL, "s:c")));
            case "del2" -> {
                ctx.clearSourceButtons();
                // LOW: xavfli yakuniy tugmada bir martalik nonce — eski xabardagi tugma keyinroq bosilsa ishlamaydi
                String nonce = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xffffffffffL);
                ctx.session.put("delNonce", nonce);
                ctx.send(Texts.DELETE_STEP2, Kb.create()
                        .row(Kb.cb(Texts.BTN_DELETE_FINAL, "s:del3:" + nonce), Kb.cb(Texts.BTN_CANCEL, "s:c")));
            }
            case "del3" -> {
                String expected = ctx.session.str("delNonce");
                if (p.length < 3 || expected == null || !expected.equals(p[2])) {
                    ctx.clearSourceButtons();
                    ctx.send(Texts.STALE);
                    return;
                }
                ctx.session.data().remove("delNonce");
                ctx.clearSourceButtons();
                accounts.requestDeletion(ctx.userId());
                ctx.session.clear();
                ctx.send(Texts.DELETE_DONE + "\n\n" + Texts.ACCOUNT_DELETION_REQUESTED, new Tg.RemoveKeyboard(true));
                ctx.send(Texts.DELETION_CANCEL_PROMPT, Kb.create().row(Kb.cb(Texts.BTN_DELETION_CANCEL, "dc:x")).build());
            }
            default -> ctx.send(Texts.STALE);
        }
    }

    /** V1+ (TZ 5-modul): mahsulotlar va hisoblashlar CSV fayl bo'lib yuboriladi (10 daqiqada bir marta). */
    private void exportData(Ctx ctx) {
        if (exporter == null) {
            ctx.send(Texts.STALE);
            return;
        }
        var next = exporter.nextAllowedAt(ctx.businessId());
        if (next.isPresent()) {
            long min = Math.max(1, (java.time.Duration.between(java.time.Instant.now(), next.get()).toSeconds() + 59) / 60);
            ctx.send(String.format(Texts.EXPORT_COOLDOWN, min), Kb.create().row(Kb.cb(Texts.BTN_BACK, "s:c")));
            return;
        }
        var e = exporter.export(ctx.businessId());
        String date = java.time.LocalDate.now(ZoneId.of("Asia/Tashkent")).toString();
        ctx.sendDocument("sellerflow-mahsulotlar-" + date + ".csv", e.products(),
                String.format(Texts.EXPORT_PRODUCTS_CAPTION, e.productRows()));
        ctx.sendDocument("sellerflow-hisoblashlar-" + date + ".csv", e.history(),
                String.format(Texts.EXPORT_HISTORY_CAPTION, e.historyRows()));
        ctx.send(Texts.EXPORT_DONE, Kb.create().row(Kb.cb(Texts.BTN_BACK, "s:c")));
    }

    /** V1+: kanallar bo'yicha saqlangan stavkalar ro'yxati, o'chirish tugmasi bilan. */
    private void showRates(Ctx ctx) {
        var list = rates == null ? List.<uz.sellerflow.channel.ChannelRateService.ChannelRates>of() : rates.list(ctx.businessId());
        if (list.isEmpty()) {
            ctx.send(Texts.RATES_NONE, Kb.create().row(Kb.cb(Texts.BTN_BACK, "s:c")));
            return;
        }
        StringBuilder sb = new StringBuilder(Texts.RATES_TITLE);
        Kb kb = Kb.create();
        for (var r : list) {
            sb.append("\n🛒 <b>").append(r.channel().label()).append("</b>\n");
            r.expenses().fields().forEachRemaining(en -> {
                try {
                    var c = uz.sellerflow.calculation.CalcTypes.ExpenseCategory.byCode(en.getKey());
                    sb.append("• ").append(CalcModel.label(c)).append(": ")
                            .append(CalcModel.describe(CalcModel.fromNode(c, en.getValue()))).append("\n");
                } catch (RuntimeException ignored) {
                    // noma'lum kod
                }
            });
            kb.row(Kb.cb("🗑 " + r.channel().label(), "s:crd:" + r.channel().name()));
        }
        ctx.send(sb.toString(), kb.row(Kb.cb(Texts.BTN_BACK, "s:c")));
    }

    /** Sotuvchining faol va kutilayotgan admin kirish ruxsatlari (bekor qilish tugmasi bilan). */
    private void showAccess(Ctx ctx) {
        List<uz.sellerflow.access.AccessGrantService.Grant> list = grants == null ? List.of() : grants.activeForBusiness(ctx.businessId());
        if (list.isEmpty()) {
            ctx.send(Texts.ACCESS_NONE, Kb.create().row(Kb.cb(Texts.BTN_BACK, "s:c")));
            return;
        }
        StringBuilder sb = new StringBuilder(Texts.ACCESS_LIST_TITLE);
        Kb kb = Kb.create();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Asia/Tashkent"));
        for (var g : list) {
            boolean approved = grants.statusOf(g) == uz.sellerflow.access.AccessGrantService.Status.APPROVED;
            sb.append("\n\n• #").append(g.getId()).append(" — ")
                    .append(approved ? "ruxsat berilgan, " + hm.format(g.getExpiresAt()) + " gacha" : "javob kutilmoqda")
                    .append("\nSabab: ").append(Ctx.esc(g.getPurpose()));
            kb.row(Kb.cb((approved ? "🚫 Bekor qilish #" : "❌ Rad etish #") + g.getId(), "g:" + g.getId() + (approved ? ":r" : ":d")));
        }
        kb.row(Kb.cb(Texts.BTN_BACK, "s:c"));
        ctx.send(sb.toString(), kb);
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
        String field = s.str("field");
        String prompt = "BN".equals(field) ? Texts.ASK_S_BNAME
                : "MM".equals(field) ? String.format(Texts.ASK_S_MIN_MARGIN, ctx.business().getMinMarginPct() == null ? "o‘rnatilmagan"
                : uz.sellerflow.calculation.Money.formatPercent(ctx.business().getMinMarginPct()) + "%")
                : Texts.ASK_S_DNAME;
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
        if ("MM".equals(s.str("field"))) {
            java.math.BigDecimal m = null;
            if (value != null) {
                var parsed = uz.sellerflow.calculation.Money.parsePercent(value);
                if (parsed.isEmpty() || parsed.get().compareTo(new java.math.BigDecimal("100")) >= 0) {
                    ctx.send(Texts.ERR_MIN_MARGIN);
                    return;
                }
                m = parsed.get();
            }
            accounts.updateMinMargin(ctx.businessId(), m);
        } else if ("BN".equals(s.str("field"))) accounts.updateBusinessName(ctx.businessId(), value);
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
