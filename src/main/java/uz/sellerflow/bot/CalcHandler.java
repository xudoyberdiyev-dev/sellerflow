package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.calculation.CalcTypes.Accuracy;
import uz.sellerflow.calculation.CalcTypes.AmountType;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.CalcTypes.ExpenseStatus;
import uz.sellerflow.calculation.CalcTypes.PriceGoal;
import uz.sellerflow.calculation.CalcTypes.PriceStatus;
import uz.sellerflow.calculation.CalculationEngine;
import uz.sellerflow.calculation.CalculationEngine.ExpenseLine;
import uz.sellerflow.calculation.CalculationEngine.PriceResult;
import uz.sellerflow.calculation.CalculationEngine.ProfitResult;
import uz.sellerflow.calculation.Expense;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.Product;
import uz.sellerflow.catalog.ProductService;
import uz.sellerflow.common.Errors;
import uz.sellerflow.history.HistoryEntry;
import uz.sellerflow.history.HistoryService;
import uz.sellerflow.history.HistoryService.SaveCommand;
import uz.sellerflow.plan.PlanService;
import uz.sellerflow.telegram.Tg.Kb;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 💰 Foydani hisoblash (SF-PRD-02.04) va 🏷 Narxni aniqlash (SF-PRD-02.05) Wizard'lari.
 *
 * Qadamlar: G=usul, C=tannarx, P=sotish narxi, KT/KV=komissiya, LT/LV=logistika, X/XT/XV=qo'shimcha,
 * T=maqsad, N=nom, CH=kanal, R=tekshirish, RS=natija.
 * Callback: w:{revision}:{step}:{action}[:arg] — revision yoki qadam mos kelmasa, tugma eskirgan hisoblanadi.
 */
@Component
public class CalcHandler {

    static final String G = "G", C = "C", P = "P", KT = "KT", KV = "KV", LT = "LT", LV = "LV",
            X = "X", XT = "XT", XV = "XV", T = "T", N = "N", CH = "CH", R = "R", RS = "RS";

    private final ProductService products;
    private final HistoryService history;
    private final PlanService plans;
    private final ObjectMapper mapper;
    private BotDispatcher dispatcher;

    public CalcHandler(ProductService products, HistoryService history, PlanService plans, ObjectMapper mapper) {
        this.products = products;
        this.history = history;
        this.plans = plans;
        this.mapper = mapper;
    }

    void bind(BotDispatcher d) { this.dispatcher = d; }

    // ================================================================= entry points

    /** Menyu tugmasi. Shu turdagi qoralama bo'lsa — Davom ettirish / Yangidan boshlash (UX-DEC-16). */
    public void enter(Ctx ctx, String kind) {
        if (!entitled(ctx, kind)) return;
        Session s = ctx.session;
        boolean sameDraft = s.isFlow(kind) && s.lastInputAt() != null && !s.data().path("_done").asBoolean(false);
        if (sameDraft) {
            ctx.send(String.format(Texts.RESUME_PROMPT, BotDispatcher.flowTitle(kind)), Kb.create()
                    .row(Kb.cb(Texts.BTN_CONTINUE, "rs:" + s.revision() + ":c"),
                            Kb.cb(Texts.BTN_RESTART, "rs:" + s.revision() + ":n")));
            return;
        }
        dispatcher.requestStart(ctx, kind);
    }

    private boolean entitled(Ctx ctx, String kind) {
        String feature = Session.PROFIT.equals(kind) ? "PROFIT_CALCULATION" : "PRICE_CALCULATION";
        if (plans.isAllowed(ctx.business().getPlanCode(), feature)) return true;
        // UX-DEC-18: sabab + Tariflar / Bosh menyu, avtomatik redirect yo'q
        ctx.send(Texts.FEATURE_LIMITED, Kb.create()
                .row(Kb.cb(Texts.BTN_TARIFFS_INLINE, "t:show"), Kb.cb(Texts.BTN_MAIN, "m")));
        return false;
    }

    void startNew(Ctx ctx, String kind) {
        Session s = ctx.session;
        s.start(kind, Session.PROFIT.equals(kind) ? C : G);
        s.put("ch", ctx.business().getPrimaryChannel().name());
        ctx.send(Session.PROFIT.equals(kind) ? Texts.PROFIT_TITLE : Texts.PRICE_TITLE);
        render(ctx);
    }

    /** Katalogdan: joriy tannarx (va reja narxi) taklif qilinadi; boshqa xarajatlar 0 deb olinmaydi (UX-PROD-033..035). */
    void startFromProduct(Ctx ctx, String kind, long productId) {
        if (!entitled(ctx, kind)) return;
        Product p = products.get(ctx.businessId(), productId);
        Session s = ctx.session;
        s.start(kind, G);
        s.put("ch", ctx.business().getPrimaryChannel().name());
        s.put("pid", p.getId());
        s.put("name", p.getName());
        s.put("cost", p.getUnitCost());
        if (Session.PROFIT.equals(kind)) {
            if (p.getPlannedSalePrice() != null) {
                s.put("price", p.getPlannedSalePrice());
                s.step(KT);
            } else {
                s.step(P);
            }
        }
        s.markInput();
        ctx.send((Session.PROFIT.equals(kind) ? Texts.PROFIT_TITLE : Texts.PRICE_TITLE)
                + "\n📦 " + Ctx.esc(p.getName()) + " · tannarx: " + Money.format(p.getUnitCost()) + " so‘m"
                + (p.getPlannedSalePrice() != null && Session.PROFIT.equals(kind)
                ? " · narx: " + Money.format(p.getPlannedSalePrice()) + " so‘m" : ""));
        render(ctx);
    }

    /** Narx natijasini foyda kalkulyatorida ochish (PRICE-FLOW-11). */
    void startProfitFromPrice(Ctx ctx) {
        Session s = ctx.session;
        if (!s.isFlow(Session.PRICE)) {
            ctx.send(Texts.STALE);
            return;
        }
        PriceResult r = CalcModel.computePrice(s);
        if (r.status() != PriceStatus.OK) {
            ctx.send(Texts.STALE);
            return;
        }
        var exp = s.data().get("exp");
        String cost = s.str("cost"), name = s.str("name"), ch = s.str("ch");
        Long pid = s.lng("pid");
        s.start(Session.PROFIT, R);
        s.put("cost", cost);
        s.put("price", r.recommendedPrice());
        s.put("name", name);
        s.put("ch", ch);
        s.put("pid", pid);
        if (exp != null) s.data().set("exp", exp.deepCopy());
        s.markInput();
        ctx.send(Texts.PROFIT_TITLE);
        render(ctx);
    }

    // ================================================================= rendering

    public void render(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        String step = s.step();
        switch (step) {
            case G -> ctx.send(Texts.ASK_GOAL, Kb.create()
                    .row(btn(rev, G, Texts.BTN_GOAL_PROFIT, "g:TARGET_PROFIT"))
                    .row(btn(rev, G, Texts.BTN_GOAL_MARGIN, "g:TARGET_MARGIN"))
                    .row(btn(rev, G, Texts.BTN_GOAL_BE, "g:BREAK_EVEN"))
                    .row(cancelBtn(rev, G)));
            case C -> ctx.send(Texts.ASK_COST, navKb(s, C));
            case P -> ctx.send(Texts.ASK_SALE_PRICE, navKb(s, P));
            case KT -> ctx.send(Texts.ASK_COMMISSION, typeKb(rev, KT, ExpenseCategory.COMMISSION));
            case LT -> ctx.send(Texts.ASK_LOGISTICS, typeKb(rev, LT, ExpenseCategory.LOGISTICS));
            case KV, LV, XV -> ctx.send("PCT".equals(s.str("pt")) ? Texts.ASK_PERCENT : Texts.ASK_AMOUNT, navKb(s, step));
            case X -> renderExtras(ctx);
            case XT -> {
                ExpenseCategory c = ExpenseCategory.byCode(s.str("px"));
                String text = String.format(Texts.ASK_EXTRA, CalcModel.label(c))
                        + (c == ExpenseCategory.TAX ? Texts.TAX_NOTE : "");
                ctx.send(text, typeKb(rev, XT, c));
            }
            case T -> ctx.send(PriceGoal.TARGET_MARGIN.name().equals(s.str("goal"))
                    ? Texts.ASK_TARGET_MARGIN : Texts.ASK_TARGET_PROFIT, navKb(s, T));
            case N -> ctx.send(Texts.ASK_NAME, Kb.create()
                    .row(btn(rev, N, Texts.BTN_NO_NAME, "noname"))
                    .row(btn(rev, N, Texts.BTN_BACK, "back")));
            case CH -> {
                List<uz.sellerflow.telegram.Tg.InlineButton> bs = new ArrayList<>();
                for (SalesChannel c : SalesChannel.values()) bs.add(btn(rev, CH, c.label(), "ch:" + c.name()));
                ctx.send(Texts.ASK_CALC_CHANNEL, Kb.create().rows(bs, 2).row(btn(rev, CH, Texts.BTN_BACK, "back")));
            }
            case R -> renderReview(ctx);
            case RS -> renderResult(ctx);
            default -> ctx.sendMenu(Texts.MAIN_MENU);
        }
    }

    private void renderExtras(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        StringBuilder lines = new StringBuilder();
        List<uz.sellerflow.telegram.Tg.InlineButton> bs = new ArrayList<>();
        for (ExpenseCategory c : ExpenseCategory.values()) {
            if (!c.isExtra()) continue;
            Expense e = CalcModel.expense(s, c);
            lines.append("• ").append(CalcModel.label(c)).append(": <b>").append(CalcModel.describe(e)).append("</b>\n");
            bs.add(btn(rev, X, CalcModel.label(c), "x:" + c.code()));
        }
        ctx.send(String.format(Texts.EXTRAS, lines.toString().stripTrailing()), Kb.create()
                .rows(bs, 2)
                .row(btn(rev, X, Texts.BTN_EXTRAS_ZERO, "allz"))
                .row(btn(rev, X, Texts.BTN_BACK, "back"), btn(rev, X, Texts.BTN_NEXT, "next")));
    }

    private void renderReview(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        boolean profit = s.isFlow(Session.PROFIT);
        StringBuilder sb = new StringBuilder("🔎 <b>Ma’lumotlarni tekshiring</b>\n\n");
        sb.append("📦 Mahsulot: <b>").append(s.str("name") == null ? "—" : Ctx.esc(s.str("name"))).append("</b>\n");
        sb.append("🛒 Kanal: <b>").append(CalcModel.channelLabel(s.str("ch"))).append("</b>\n");
        if (!profit) {
            sb.append("🎯 Usul: <b>").append(CalcModel.goalLabel(s.str("goal"))).append("</b>\n");
            String goal = s.str("goal");
            if (PriceGoal.TARGET_PROFIT.name().equals(goal)) {
                sb.append("💵 Maqsadli foyda: <b>").append(Money.format(s.dec("target"))).append(" so‘m</b>\n");
            } else if (PriceGoal.TARGET_MARGIN.name().equals(goal)) {
                sb.append("📈 Maqsadli marja: <b>").append(Money.formatPercent(s.dec("target"))).append("%</b>\n");
            }
        }
        sb.append("\n💵 Tannarx: <b>").append(Money.format(s.dec("cost"))).append(" so‘m</b>\n");
        if (profit) sb.append("🏷 Sotish narxi: <b>").append(Money.format(s.dec("price"))).append(" so‘m</b>\n");
        for (ExpenseCategory c : ExpenseCategory.values()) {
            sb.append("• ").append(CalcModel.label(c)).append(": ")
                    .append(CalcModel.describe(CalcModel.expense(s, c))).append("\n");
        }
        Kb kb = Kb.create().row(btn(rev, R, Texts.BTN_CALCULATE, "calc"));
        if (profit) {
            kb.row(btn(rev, R, Texts.BTN_E_COST, "e:C"), btn(rev, R, Texts.BTN_E_PRICE, "e:P"));
        } else {
            kb.row(btn(rev, R, Texts.BTN_E_GOAL, "e:G"), btn(rev, R, Texts.BTN_E_COST, "e:C"));
            if (!PriceGoal.BREAK_EVEN.name().equals(s.str("goal"))) kb.row(btn(rev, R, Texts.BTN_E_TARGET, "e:T"));
        }
        kb.row(btn(rev, R, Texts.BTN_E_COMM, "e:KT"), btn(rev, R, Texts.BTN_E_LOG, "e:LT"))
                .row(btn(rev, R, Texts.BTN_E_EXTRAS, "e:X"))
                .row(btn(rev, R, Texts.BTN_E_NAME, "e:N"), btn(rev, R, Texts.BTN_E_CHANNEL, "e:CH"))
                .row(cancelBtn(rev, R));
        ctx.send(sb.toString(), kb);
    }

    private void renderResult(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        boolean saved = s.str("rid") != null && s.str("rid").equals(s.str("saved"));
        if (s.isFlow(Session.PROFIT)) {
            ProfitResult r = CalcModel.computeProfit(s);
            Kb kb = Kb.create();
            if (!saved) kb.row(btn(rev, RS, Texts.BTN_SAVE, "save"));
            kb.row(btn(rev, RS, Texts.BTN_EDIT, "edit"), Kb.cb(Texts.BTN_MAIN, "m"));
            ctx.send(formatProfit(s.str("name"), r), kb);
        } else {
            PriceResult r = CalcModel.computePrice(s);
            Kb kb = Kb.create();
            if (r.status() == PriceStatus.OK) {
                BigDecimal step = CalcModel.roundingStep(s);
                List<uz.sellerflow.telegram.Tg.InlineButton> steps = new ArrayList<>();
                for (String v : new String[]{"1", "100", "1000"}) {
                    boolean cur = step.compareTo(new BigDecimal(v)) == 0;
                    steps.add(btn(rev, RS, (cur ? "✓ " : "") + Money.format(new BigDecimal(v)), "s:" + v));
                }
                kb.row(steps.toArray(new uz.sellerflow.telegram.Tg.InlineButton[0]));
                if (!saved) kb.row(btn(rev, RS, Texts.BTN_SAVE, "save"));
                kb.row(btn(rev, RS, Texts.BTN_TO_PROFIT, "toprofit"));
            }
            kb.row(btn(rev, RS, Texts.BTN_EDIT, "edit"), Kb.cb(Texts.BTN_MAIN, "m"));
            ctx.send(formatPrice(s.str("name"), r), kb);
        }
    }

    // ================================================================= formatting (shared with history)

    static String formatProfit(String name, ProfitResult r) {
        StringBuilder sb = new StringBuilder("💰 <b>Foyda hisobi</b>");
        if (name != null) sb.append(" — ").append(Ctx.esc(name));
        sb.append("\n\n🏷 Sotish narxi: <b>").append(Money.format(r.salePrice())).append(" so‘m</b>\n");
        sb.append("− Tannarx: ").append(Money.format(r.unitCost())).append("\n");
        appendLines(sb, r.lines());
        sb.append("= Jami ma’lum xarajat: <b>").append(Money.format(r.knownExpensesTotal())).append("</b>\n\n");
        String icon = switch (r.outcome()) {
            case PROFIT -> "✅ Foyda";
            case BREAK_EVEN -> "⚖️ Zararsiz (0)";
            case LOSS -> "🔻 Zarar";
        };
        sb.append(icon).append(": <b>").append(Money.format(r.profit())).append(" so‘m</b>\n");
        sb.append("📈 Marja: <b>").append(Money.formatPercent(r.marginPct())).append("%</b>\n\n");
        appendAccuracy(sb, r.accuracy(), r.missing());
        return sb.toString();
    }

    static String formatPrice(String name, PriceResult r) {
        StringBuilder sb = new StringBuilder("🏷 <b>Narx tavsiyasi</b>");
        if (name != null) sb.append(" — ").append(Ctx.esc(name));
        sb.append("\n🎯 ").append(CalcModel.goalLabel(r.goal().name()));
        if (r.targetProfit() != null) sb.append(": kamida ").append(Money.format(r.targetProfit())).append(" so‘m foyda");
        if (r.targetMarginPct() != null) sb.append(": kamida ").append(Money.formatPercent(r.targetMarginPct())).append("% marja");
        sb.append("\n\n");
        switch (r.status()) {
            case OK -> {
                sb.append("📐 Matematik chegara: ").append(Money.format(r.rawPrice())).append(" so‘m\n");
                sb.append("✅ <b>Tavsiya narxi: ").append(Money.format(r.recommendedPrice())).append(" so‘m</b> (")
                        .append(Money.format(r.roundingStep())).append(" so‘m qadamda yuqoriga yaxlitlangan)\n\n");
                ProfitResult v = r.verification();
                sb.append("Shu narxda tekshiruv:\n− Tannarx: ").append(Money.format(v.unitCost())).append("\n");
                appendLines(sb, v.lines());
                sb.append("= Foyda: <b>").append(Money.format(v.profit())).append(" so‘m</b> · Marja: <b>")
                        .append(Money.formatPercent(v.marginPct())).append("%</b>\n\n");
            }
            case NO_SOLUTION -> sb.append("❌ <b>Bu shartlarda hech qanday musbat narx maqsadga yetmaydi.</b>\n")
                    .append("Sotish narxidan olinadigan foizlar yig‘indisi: ")
                    .append(Money.formatPercent(r.percentRate().multiply(Money.HUNDRED))).append("%")
                    .append(r.goal() == PriceGoal.TARGET_MARGIN ? " + maqsadli marja" : "")
                    .append(" — 100% yoki undan ko‘p. Foizlarni yoki maqsadni kamaytiring.\n\n");
            case NO_UNIQUE_PRICE -> sb.append("ℹ️ <b>Yagona narx yo‘q:</b> ma’lum qat’iy xarajatlar 0 bo‘lgani uchun ")
                    .append("har qanday musbat narx bu maqsadga mos keladi. Tannarx yoki xarajatlarni tekshiring.\n\n");
            case OUT_OF_RANGE -> sb.append("❌ Natija texnik diapazondan tashqarida. Qiymatlarni tekshiring.\n\n");
        }
        appendAccuracy(sb, r.accuracy(), r.missing());
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, List<ExpenseLine> lines) {
        for (ExpenseLine l : lines) {
            sb.append("− ").append(CalcModel.label(l.category()));
            if (l.percent() != null) sb.append(" (").append(Money.formatPercent(l.percent())).append("%)");
            sb.append(": ");
            if (l.amount() != null) {
                sb.append(Money.format(l.amount()));
            } else {
                sb.append(l.status() == ExpenseStatus.UNKNOWN ? "<i>noma’lum</i>" : "<i>kiritilmagan</i>");
            }
            sb.append("\n");
        }
    }

    private static void appendAccuracy(StringBuilder sb, Accuracy accuracy, List<ExpenseCategory> missing) {
        if (accuracy == Accuracy.PARTIAL_ESTIMATE) {
            sb.append(String.format(Texts.PARTIAL_WARNING, CalcModel.missingList(missing))).append("\n\n");
        } else {
            sb.append("✅ Barcha xarajatlar kiritilgan.\n\n");
        }
        sb.append(Texts.DISCLAIMER);
    }

    // ================================================================= text input

    public void onText(Ctx ctx, String text) {
        Session s = ctx.session;
        String step = s.step();
        switch (step) {
            case C -> {
                Optional<BigDecimal> v = money(ctx, text);
                if (v.isEmpty()) return;
                s.put("cost", v.get());
                advance(ctx, C);
            }
            case P -> {
                Optional<BigDecimal> v = money(ctx, text);
                if (v.isEmpty()) return;
                if (v.get().signum() <= 0) {
                    ctx.send(Texts.ERR_PRICE_POSITIVE);
                    return;
                }
                s.put("price", v.get());
                advance(ctx, P);
            }
            case KV, LV, XV -> {
                ExpenseCategory c = step.equals(KV) ? ExpenseCategory.COMMISSION
                        : step.equals(LV) ? ExpenseCategory.LOGISTICS : ExpenseCategory.byCode(s.str("px"));
                boolean pct = "PCT".equals(s.str("pt"));
                Optional<BigDecimal> v = pct ? percent(ctx, text) : money(ctx, text);
                if (v.isEmpty()) return;
                CalcModel.setExpense(s, new Expense(c, ExpenseStatus.KNOWN,
                        pct ? AmountType.PERCENT_OF_SALE_PRICE : AmountType.FIXED_PER_UNIT, v.get()));
                advance(ctx, step);
            }
            case T -> {
                boolean margin = PriceGoal.TARGET_MARGIN.name().equals(s.str("goal"));
                Optional<BigDecimal> v;
                if (margin) {
                    v = Money.parse(text);
                    if (v.isEmpty()) { ctx.send(Texts.ERR_NUMBER); return; }
                    if (v.get().compareTo(Money.HUNDRED) >= 0) { ctx.send(Texts.ERR_MARGIN_RANGE); return; }
                } else {
                    v = money(ctx, text);
                    if (v.isEmpty()) return;
                }
                s.put("target", v.get());
                advance(ctx, T);
            }
            case N -> {
                String name;
                try {
                    name = ProductService.validateName(text);
                } catch (Errors.Validation e) {
                    ctx.send(BotDispatcher.validationMessage(e.code()));
                    return;
                }
                s.put("name", name);
                advance(ctx, N);
            }
            default -> ctx.send(Texts.EXPECT_BUTTON);
        }
    }

    private Optional<BigDecimal> money(Ctx ctx, String text) {
        Optional<BigDecimal> v = Money.parse(text);
        if (v.isEmpty()) {
            ctx.send(Texts.ERR_NUMBER);
            return Optional.empty();
        }
        if (v.get().compareTo(Money.MAX) > 0) {
            ctx.send(Texts.ERR_TOO_LARGE);
            return Optional.empty();
        }
        return v;
    }

    private Optional<BigDecimal> percent(Ctx ctx, String text) {
        Optional<BigDecimal> v = Money.parse(text);
        if (v.isEmpty()) {
            ctx.send(Texts.ERR_NUMBER);
            return Optional.empty();
        }
        if (v.get().compareTo(Expense.MAX_PERCENT) > 0) {
            ctx.send(Texts.ERR_PERCENT_RANGE);
            return Optional.empty();
        }
        return v;
    }

    // ================================================================= callbacks

    public void onCallback(Ctx ctx, String[] p) {
        Session s = ctx.session;
        // w:{rev}:{step}:{action}[:arg]
        if (p.length < 4 || !(s.isFlow(Session.PROFIT) || s.isFlow(Session.PRICE))
                || !String.valueOf(s.revision()).equals(p[1]) || !p[2].equals(s.step())) {
            ctx.send(Texts.STALE);
            return;
        }
        s.resume();
        ctx.clearSourceButtons();
        String step = p[2];
        String action = p[3];
        String arg = p.length > 4 ? p[4] : null;

        if ("cancel".equals(action)) {
            s.clear();
            ctx.sendMenu(Texts.CANCELLED);
            return;
        }
        if ("back".equals(action)) {
            s.step(prevStep(s, step));
            render(ctx);
            return;
        }
        switch (step) {
            case G -> {
                if (!"g".equals(action)) break;
                s.put("goal", PriceGoal.valueOf(arg).name());
                if (PriceGoal.BREAK_EVEN.name().equals(arg)) s.data().remove("target");
                s.markInput();
                if (R.equals(s.str("ret"))) {
                    // Usul o'zgarsa, maqsad qiymati qayta so'raladi
                    s.data().remove("ret");
                    s.step(PriceGoal.BREAK_EVEN.name().equals(arg) ? R : T);
                    if (!PriceGoal.BREAK_EVEN.name().equals(arg)) s.put("ret", R);
                    s.data().remove("target");
                } else {
                    s.step(s.str("cost") == null ? C : KT);
                }
                render(ctx);
                return;
            }
            case KT, LT, XT -> {
                ExpenseCategory c = step.equals(KT) ? ExpenseCategory.COMMISSION
                        : step.equals(LT) ? ExpenseCategory.LOGISTICS : ExpenseCategory.byCode(s.str("px"));
                switch (action) {
                    case "pct" -> {
                        if (!c.allowsPercent()) break;
                        s.put("pt", "PCT");
                        s.step(valueStep(step));
                    }
                    case "fix" -> {
                        s.put("pt", "FIX");
                        s.step(valueStep(step));
                    }
                    case "zero" -> {
                        CalcModel.setExpense(s, Expense.zero(c));
                        advance(ctx, step);
                        return;
                    }
                    case "unk" -> {
                        CalcModel.setExpense(s, Expense.unknown(c));
                        advance(ctx, step);
                        return;
                    }
                    default -> { }
                }
                render(ctx);
                return;
            }
            case X -> {
                switch (action) {
                    case "x" -> {
                        s.put("px", ExpenseCategory.byCode(arg).code());
                        s.step(XT);
                    }
                    case "allz" -> {
                        for (ExpenseCategory c : ExpenseCategory.values()) {
                            if (c.isExtra() && CalcModel.expense(s, c).status() == ExpenseStatus.NOT_SET) {
                                CalcModel.setExpense(s, Expense.zero(c));
                            }
                        }
                        s.markInput();
                        s.step(afterExtras(s));
                    }
                    case "next" -> {
                        s.markInput();
                        s.step(afterExtras(s));
                    }
                    default -> { }
                }
                render(ctx);
                return;
            }
            case N -> {
                if ("noname".equals(action)) {
                    s.data().remove("name");
                    s.data().remove("pid");
                    advance(ctx, N);
                    return;
                }
            }
            case CH -> {
                if ("ch".equals(action)) {
                    s.put("ch", SalesChannel.valueOf(arg).name());
                    advance(ctx, CH);
                    return;
                }
            }
            case R -> {
                if ("calc".equals(action)) {
                    calculate(ctx);
                    return;
                }
                if ("e".equals(action)) {
                    s.put("ret", R);
                    s.step(arg);
                    render(ctx);
                    return;
                }
            }
            case RS -> {
                switch (action) {
                    case "save" -> { save(ctx); return; }
                    case "edit" -> { s.step(R); render(ctx); return; }
                    case "s" -> {
                        BigDecimal stepValue = new BigDecimal(arg);
                        if (CalculationEngine.ALLOWED_ROUNDING_STEPS.stream().anyMatch(x -> x.compareTo(stepValue) == 0)) {
                            s.put("rs", stepValue);
                            newResultId(s);
                            s.markInput();
                        }
                        render(ctx);
                        return;
                    }
                    case "toprofit" -> { dispatcher.requestStart(ctx, "TOPROFIT"); return; }
                    default -> { }
                }
            }
            default -> { }
        }
        ctx.send(Texts.STALE);
    }

    // ================================================================= transitions

    private static String valueStep(String typeStep) {
        return switch (typeStep) {
            case KT -> KV;
            case LT -> LV;
            default -> XV;
        };
    }

    /** Qiymat qabul qilingach keyingi qadam. Review'dan tahrir qilinayotgan bo'lsa — Review'ga qaytadi. */
    private void advance(Ctx ctx, String from) {
        Session s = ctx.session;
        s.markInput();
        boolean profit = s.isFlow(Session.PROFIT);
        boolean returnToReview = R.equals(s.str("ret"));
        String next = switch (from) {
            case C -> profit ? P : KT;
            case P -> KT;
            case KT, KV -> LT;
            case LT, LV -> X;
            case XT, XV -> X;
            case T -> R;
            case N, CH -> R;
            default -> R;
        };
        if (returnToReview && !from.equals(XT) && !from.equals(XV)) {
            next = R;
            s.data().remove("ret");
        }
        if (profit && next.equals(R) && s.str("price") == null) next = P;
        if (next.equals(R) && s.str("cost") == null) next = C;
        s.step(next);
        render(ctx);
    }

    private String afterExtras(Session s) {
        if (R.equals(s.str("ret"))) {
            s.data().remove("ret");
            return R;
        }
        if (s.isFlow(Session.PROFIT)) return R;
        return PriceGoal.BREAK_EVEN.name().equals(s.str("goal")) ? R : T;
    }

    private String prevStep(Session s, String step) {
        if (R.equals(s.str("ret")) && !step.equals(XT) && !step.equals(XV)) {
            s.data().remove("ret");
            return R;
        }
        boolean profit = s.isFlow(Session.PROFIT);
        return switch (step) {
            case C -> profit ? C : G;
            case P -> C;
            case KT -> profit ? P : C;
            case KV -> KT;
            case LT -> KT;
            case LV -> LT;
            case X -> LT;
            case XT -> X;
            case XV -> XT;
            case T -> X;
            case N, CH -> R;
            default -> step;
        };
    }

    // ================================================================= calculate / save

    private void calculate(Ctx ctx) {
        Session s = ctx.session;
        if (s.str("cost") == null || (s.isFlow(Session.PROFIT) && s.str("price") == null)
                || (s.isFlow(Session.PRICE) && s.str("goal") == null)) {
            s.step(s.str("cost") == null ? C : (s.isFlow(Session.PROFIT) ? P : G));
            render(ctx);
            return;
        }
        if (s.isFlow(Session.PRICE) && !PriceGoal.BREAK_EVEN.name().equals(s.str("goal")) && s.str("target") == null) {
            s.step(T);
            render(ctx);
            return;
        }
        newResultId(s);
        s.data().remove("_done");
        s.step(RS);
        render(ctx);
    }

    private static void newResultId(Session s) {
        s.put("rid", UUID.randomUUID().toString());
        s.put("cat", Instant.now().toString());
    }

    /** Qo'lda Save: aynan bitta tarix yozuvi; takror bosish dublikat yaratmaydi (UX-CALC-038, UX-HIST-006). */
    private void save(Ctx ctx) {
        Session s = ctx.session;
        String rid = s.str("rid");
        if (rid == null) {
            ctx.send(Texts.STALE);
            return;
        }
        boolean profit = s.isFlow(Session.PROFIT);
        String kind = s.flowType();
        String input = CalcModel.inputSnapshot(mapper, s, kind);
        String expenses;
        String output;
        String accuracy;
        if (profit) {
            ProfitResult r = CalcModel.computeProfit(s);
            expenses = CalcModel.expenseSnapshot(mapper, s, r.lines());
            output = CalcModel.profitOutput(mapper, r);
            accuracy = r.accuracy().name();
        } else {
            PriceResult r = CalcModel.computePrice(s);
            if (r.status() != PriceStatus.OK) {
                ctx.send(Texts.STALE);
                return;
            }
            expenses = CalcModel.expenseSnapshot(mapper, s, r.verification().lines());
            output = CalcModel.priceOutput(mapper, r);
            accuracy = r.accuracy().name();
        }
        Long pid = s.lng("pid");
        if (pid != null && products.find(ctx.businessId(), pid).isEmpty()) pid = null;
        HistoryService.SaveResult res = history.save(new SaveCommand(ctx.businessId(), ctx.userId(), rid,
                profit ? HistoryEntry.Type.PROFIT : HistoryEntry.Type.PRICE, pid, s.str("name"), s.str("ch"),
                input, expenses, output, accuracy, CalculationEngine.VERSION,
                Instant.parse(s.str("cat")), s.lng("src")));
        s.put("saved", rid);
        s.put("hid", res.entry().getId());
        s.markSaved();
        s.data().put("_done", true);
        ctx.send(res.created() ? Texts.SAVED : Texts.ALREADY_SAVED, Kb.create()
                .row(Kb.cb("📊 Tarixda ko‘rish", "h:v:" + res.entry().getId()))
                .row(btn(s.revision(), RS, Texts.BTN_EDIT, "edit"), Kb.cb(Texts.BTN_MAIN, "m")));
    }

    // ================================================================= keyboards

    private static uz.sellerflow.telegram.Tg.InlineButton btn(int rev, String step, String text, String action) {
        return Kb.cb(text, "w:" + rev + ":" + step + ":" + action);
    }

    private static uz.sellerflow.telegram.Tg.InlineButton cancelBtn(int rev, String step) {
        return btn(rev, step, Texts.BTN_CANCEL, "cancel");
    }

    /** Matn kiritish qadamlari uchun: Ortga (agar oldingi qadam bo'lsa) va Bekor qilish. */
    private static Kb navKb(Session s, String step) {
        int rev = s.revision();
        boolean firstStep = step.equals(C) && s.isFlow(Session.PROFIT) && !R.equals(s.str("ret"));
        Kb kb = Kb.create();
        if (firstStep) kb.row(cancelBtn(rev, step));
        else kb.row(btn(rev, step, Texts.BTN_BACK, "back"), cancelBtn(rev, step));
        return kb;
    }

    private Kb typeKb(int rev, String step, ExpenseCategory c) {
        Kb kb = Kb.create();
        if (c.allowsPercent()) {
            kb.row(btn(rev, step, Texts.BTN_PERCENT, "pct"), btn(rev, step, Texts.BTN_AMOUNT, "fix"));
        } else {
            kb.row(btn(rev, step, Texts.BTN_AMOUNT, "fix"));
        }
        kb.row(btn(rev, step, Texts.BTN_ZERO, "zero"), btn(rev, step, Texts.BTN_UNKNOWN, "unk"));
        kb.row(btn(rev, step, Texts.BTN_BACK, "back"), cancelBtn(rev, step));
        return kb;
    }
}
