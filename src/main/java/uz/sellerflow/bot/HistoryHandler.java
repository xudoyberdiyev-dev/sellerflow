package uz.sellerflow.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.Product;
import uz.sellerflow.catalog.ProductService;
import uz.sellerflow.history.HistoryEntry;
import uz.sellerflow.history.HistoryEntry.Type;
import uz.sellerflow.history.HistoryEntry.Visibility;
import uz.sellerflow.history.HistoryService;
import uz.sellerflow.history.HistoryService.Filter;
import uz.sellerflow.telegram.Tg.Kb;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📊 Hisoblashlar tarixi (SF-PRD-02.07): yagona ro'yxat, ALL/PROFIT/PRICE filtri, snapshot nomi bo'yicha qidiruv,
 * Asia/Tashkent sana filtri, READ-ONLY tafsilot, arxiv/tiklash, eski snapshotdan yangi qoralama.
 * Filtr holati session.ui ichida (h* kalitlar).
 */
@Component
public class HistoryHandler {

    public static final String MODE_SEARCH = "HISTORY_SEARCH";
    public static final String MODE_RANGE = "HISTORY_RANGE";
    static final ZoneId TZ = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM").withZone(TZ);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(TZ);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern RANGE = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s*-\\s*(\\d{2}\\.\\d{2}\\.\\d{4})$");

    private final HistoryService history;
    private final ProductService products;
    private final CalcHandler calc;
    private final ObjectMapper mapper;
    private BotDispatcher dispatcher;

    public HistoryHandler(HistoryService history, ProductService products, CalcHandler calc, ObjectMapper mapper) {
        this.history = history;
        this.products = products;
        this.calc = calc;
        this.mapper = mapper;
    }

    void bind(BotDispatcher d) { this.dispatcher = d; }

    // ================================================================= list

    /** Bosh menyudan kirish: filtrlar tozalanadi, yangi anchor olinadi. */
    public void showFresh(Ctx ctx) {
        ObjectNode ui = ctx.session.ui();
        ui.put("hType", "A");
        ui.put("hPeriod", "A");
        ui.remove("hQ");
        ui.remove("hFrom");
        ui.remove("hTo");
        ui.put("hArch", false);
        ui.put("hAnchor", history.currentAnchor(ctx.businessId()));
        showList(ctx, 0);
    }

    private void showList(Ctx ctx, int page) {
        ObjectNode ui = ctx.session.ui();
        boolean archived = ui.path("hArch").asBoolean(false);
        Filter f = filter(ui);
        Page<HistoryEntry> pg = history.list(ctx.businessId(), f, page);
        boolean noFilters = "A".equals(ui.path("hType").asText("A")) && "A".equals(ui.path("hPeriod").asText("A"))
                && !ui.hasNonNull("hQ") && !archived;
        if (pg.getTotalElements() == 0 && noFilters) {
            ctx.send(Texts.HISTORY_EMPTY, Kb.create().row(Kb.cb("🗄 Arxiv", "h:ar")));
            return;
        }
        StringBuilder title = new StringBuilder();
        if (archived) title.append(" — 🗄 arxiv");
        title.append("\nFiltr: ").append(typeLabel(ui.path("hType").asText("A")))
                .append(" · ").append(periodLabel(ui));
        if (ui.hasNonNull("hQ")) title.append(" · «").append(Ctx.esc(ui.path("hQ").asText())).append("»");
        if (pg.isEmpty()) title.append("\n\n").append(Texts.HISTORY_FILTER_EMPTY);

        Kb kb = Kb.create();
        for (HistoryEntry e : pg.getContent()) kb.row(Kb.cb(itemLabel(e), "h:v:" + e.getId()));
        ProductHandler.pager(kb, pg, "h:l:");
        String t = ui.path("hType").asText("A");
        kb.row(Kb.cb(mark("A".equals(t)) + "Hammasi", "h:t:A"), Kb.cb(mark("P".equals(t)) + "💰 Foyda", "h:t:P"),
                Kb.cb(mark("R".equals(t)) + "🏷 Narx", "h:t:R"));
        String d = ui.path("hPeriod").asText("A");
        kb.row(Kb.cb(mark("T".equals(d)) + "Bugun", "h:d:T"), Kb.cb(mark("7".equals(d)) + "7 kun", "h:d:7"),
                Kb.cb(mark("30".equals(d)) + "30 kun", "h:d:30"));
        kb.row(Kb.cb(mark("C".equals(d)) + "📅 Oraliq", "h:d:C"), Kb.cb(mark("A".equals(d)) + "Barcha vaqt", "h:d:A"));
        kb.row(Kb.cb(Texts.BTN_SEARCH, "h:q"), Kb.cb(archived ? "📊 Faol yozuvlar" : "🗄 Arxiv", "h:ar"));
        if (ui.hasNonNull("hQ")) kb.row(Kb.cb("🧹 Qidiruvni tozalash", "h:qc"));
        ctx.send(String.format(Texts.HISTORY_TITLE, title) + ProductHandler.pageInfo(pg), kb);
    }

    private Filter filter(ObjectNode ui) {
        Type type = switch (ui.path("hType").asText("A")) {
            case "P" -> Type.PROFIT;
            case "R" -> Type.PRICE;
            default -> null;
        };
        Instant from = null, to = null;
        LocalDate today = LocalDate.now(TZ);
        switch (ui.path("hPeriod").asText("A")) {
            case "T" -> { from = startOf(today); to = startOf(today.plusDays(1)); }
            case "7" -> { from = startOf(today.minusDays(6)); to = startOf(today.plusDays(1)); }
            case "30" -> { from = startOf(today.minusDays(29)); to = startOf(today.plusDays(1)); }
            case "C" -> {
                if (ui.hasNonNull("hFrom")) from = Instant.parse(ui.path("hFrom").asText());
                if (ui.hasNonNull("hTo")) to = Instant.parse(ui.path("hTo").asText());
            }
            default -> { }
        }
        Visibility vis = ui.path("hArch").asBoolean(false) ? Visibility.ARCHIVED : Visibility.ACTIVE;
        long anchor = ui.path("hAnchor").asLong(0);
        return new Filter(type, vis, ui.hasNonNull("hQ") ? ui.path("hQ").asText() : null, from, to,
                anchor > 0 ? anchor : null);
    }

    private static Instant startOf(LocalDate d) { return d.atStartOfDay(TZ).toInstant(); }

    private static String mark(boolean on) { return on ? "✓ " : ""; }

    private static String typeLabel(String t) {
        return switch (t) { case "P" -> "foyda"; case "R" -> "narx"; default -> "hammasi"; };
    }

    private static String periodLabel(ObjectNode ui) {
        return switch (ui.path("hPeriod").asText("A")) {
            case "T" -> "bugun";
            case "7" -> "oxirgi 7 kun";
            case "30" -> "oxirgi 30 kun";
            case "C" -> ui.path("hRangeLabel").asText("oraliq");
            default -> "barcha vaqt";
        };
    }

    private String itemLabel(HistoryEntry e) {
        JsonNode out = read(e.getOutputSnapshot());
        String name = e.getProductNameSnapshot() == null ? "Nomsiz" : e.getProductNameSnapshot();
        if (name.length() > 28) name = name.substring(0, 27) + "…";
        String value;
        if (e.getType() == Type.PROFIT) {
            value = "foyda " + Money.format(new BigDecimal(out.path("profit").asText("0")));
        } else {
            value = "narx " + Money.format(new BigDecimal(out.path("recommendedPrice").asText("0")));
        }
        String est = "PARTIAL_ESTIMATE".equals(e.getAccuracyStatus()) ? " ~" : "";
        return (e.getType() == Type.PROFIT ? "💰 " : "🏷 ") + SHORT.format(e.getSavedAt()) + " · " + name + " · " + value + est;
    }

    // ================================================================= inputs

    public void search(Ctx ctx, String text) {
        String q = text.strip();
        if (q.isEmpty()) {
            ctx.send(Texts.ERR_TEXT_EMPTY);
            return;
        }
        ctx.session.ui().put("hQ", q.length() > 60 ? q.substring(0, 60) : q);
        refreshAnchor(ctx);
        showList(ctx, 0);
    }

    public void range(Ctx ctx, String text) {
        Matcher m = RANGE.matcher(text.strip());
        try {
            if (!m.matches()) throw new IllegalArgumentException();
            LocalDate a = LocalDate.parse(m.group(1), DAY);
            LocalDate b = LocalDate.parse(m.group(2), DAY);
            if (b.isBefore(a)) throw new IllegalArgumentException();
            ObjectNode ui = ctx.session.ui();
            ui.put("hPeriod", "C");
            ui.put("hFrom", startOf(a).toString());
            ui.put("hTo", startOf(b.plusDays(1)).toString()); // [from, to) — oxirgi kun ham kiradi
            ui.put("hRangeLabel", DAY.format(a) + "–" + DAY.format(b));
            refreshAnchor(ctx);
            showList(ctx, 0);
        } catch (RuntimeException e) {
            ctx.session.uiMode(MODE_RANGE);
            ctx.send(Texts.ERR_RANGE);
        }
    }

    private void refreshAnchor(Ctx ctx) {
        ctx.session.ui().put("hAnchor", history.currentAnchor(ctx.businessId()));
    }

    // ================================================================= callbacks

    public void onCallback(Ctx ctx, String[] p) {
        ObjectNode ui = ctx.session.ui();
        String a = p.length > 1 ? p[1] : "";
        switch (a) {
            case "l" -> showList(ctx, Integer.parseInt(p[2]));
            case "t" -> { ui.put("hType", p[2]); refreshAnchor(ctx); showList(ctx, 0); }
            case "d" -> {
                if ("C".equals(p[2])) {
                    ctx.session.uiMode(MODE_RANGE);
                    ctx.send(Texts.ASK_HISTORY_RANGE);
                    return;
                }
                ui.put("hPeriod", p[2]);
                refreshAnchor(ctx);
                showList(ctx, 0);
            }
            case "ar" -> {
                ui.put("hArch", !ui.path("hArch").asBoolean(false));
                refreshAnchor(ctx);
                showList(ctx, 0);
            }
            case "q" -> {
                ctx.session.uiMode(MODE_SEARCH);
                ctx.send(Texts.ASK_HISTORY_SEARCH);
            }
            case "qc" -> { ui.remove("hQ"); refreshAnchor(ctx); showList(ctx, 0); }
            case "v" -> showDetail(ctx, Long.parseLong(p[2]));
            case "a" -> ctx.send(Texts.HISTORY_ARCHIVE_CONFIRM, Kb.create()
                    .row(Kb.cb(Texts.BTN_ARCHIVE, "h:ac:" + p[2]), Kb.cb(Texts.BTN_CANCEL, "h:v:" + p[2])));
            case "ac" -> {
                ctx.clearSourceButtons();
                history.archive(ctx.businessId(), Long.parseLong(p[2]));
                ctx.send(Texts.HISTORY_ARCHIVED);
                showFresh(ctx);
            }
            case "r" -> {
                ctx.clearSourceButtons();
                history.restore(ctx.businessId(), Long.parseLong(p[2]));
                ctx.send(Texts.HISTORY_RESTORED);
                showDetail(ctx, Long.parseLong(p[2]));
            }
            case "rc" -> recalcEntry(ctx, Long.parseLong(p[2]));
            case "rcc" -> dispatcher.requestStart(ctx, "RECALC:" + Long.parseLong(p[2]) + ":" + ("c".equals(p[3]) ? "c" : "o"));
            default -> ctx.send(Texts.STALE);
        }
    }

    // ================================================================= detail (READ-ONLY snapshot)

    private void showDetail(Ctx ctx, long id) {
        HistoryEntry e = history.get(ctx.businessId(), id);
        Kb kb = Kb.create();
        if (e.getVisibility() == Visibility.ACTIVE) {
            kb.row(Kb.cb(Texts.BTN_RECALC, "h:rc:" + id), Kb.cb(Texts.BTN_ARCHIVE, "h:a:" + id));
        } else {
            kb.row(Kb.cb(Texts.BTN_RESTORE, "h:r:" + id), Kb.cb(Texts.BTN_RECALC, "h:rc:" + id));
        }
        kb.row(Kb.cb(Texts.BTN_LIST, "h:l:0"));
        ctx.send(renderSnapshot(e), kb);
    }

    String renderSnapshot(HistoryEntry e) {
        JsonNode in = read(e.getInputSnapshot());
        JsonNode ex = read(e.getExpenseSnapshot());
        JsonNode out = read(e.getOutputSnapshot());
        StringBuilder sb = new StringBuilder();
        sb.append(e.getType() == Type.PROFIT ? "💰 <b>Foyda hisobi</b>" : "🏷 <b>Narx tavsiyasi</b>");
        sb.append(" · #").append(e.getId()).append("\n");
        sb.append("📦 ").append(e.getProductNameSnapshot() == null ? "Nomsiz" : Ctx.esc(e.getProductNameSnapshot()))
                .append(" · 🛒 ").append(CalcModel.channelLabel(e.getChannelSnapshot())).append("\n");
        sb.append("🕒 Saqlangan: ").append(FULL.format(e.getSavedAt()));
        if (e.getVisibility() == Visibility.ARCHIVED) sb.append(" · 🗄 arxivda");
        sb.append("\n\n");
        if (e.getType() == Type.PROFIT) {
            sb.append("🏷 Sotish narxi: <b>").append(fmt(out, "salePrice")).append(" so‘m</b>\n");
            sb.append("− Tannarx: ").append(fmt(out, "unitCost")).append("\n");
            appendExpenses(sb, ex);
            sb.append("= Jami ma’lum xarajat: <b>").append(fmt(out, "knownExpensesTotal")).append("</b>\n\n");
            sb.append("Foyda: <b>").append(fmt(out, "profit")).append(" so‘m</b> · Marja: <b>")
                    .append(Money.formatPercent(new BigDecimal(out.path("marginPct").asText("0")))).append("%</b>\n");
        } else {
            sb.append("🎯 ").append(CalcModel.goalLabel(out.path("goal").asText(null)));
            if (out.hasNonNull("targetProfit")) sb.append(": kamida ").append(fmt(out, "targetProfit")).append(" so‘m");
            if (out.hasNonNull("targetMarginPct")) sb.append(": kamida ")
                    .append(Money.formatPercent(new BigDecimal(out.path("targetMarginPct").asText()))).append("%");
            sb.append("\n💵 Tannarx: ").append(Money.format(new BigDecimal(in.path("cost").asText("0")))).append(" so‘m\n");
            appendExpenses(sb, ex);
            sb.append("\n📐 Matematik chegara: ").append(fmt(out, "rawPrice")).append(" so‘m\n");
            sb.append("✅ Tavsiya narxi: <b>").append(fmt(out, "recommendedPrice")).append(" so‘m</b> (")
                    .append(fmt(out, "roundingStep")).append(" so‘m qadam)\n");
            sb.append("Shu narxda foyda: <b>").append(fmt(out, "profitAtPrice")).append(" so‘m</b> · Marja: <b>")
                    .append(Money.formatPercent(new BigDecimal(out.path("marginAtPrice").asText("0")))).append("%</b>\n");
        }
        sb.append("\n").append("PARTIAL_ESTIMATE".equals(e.getAccuracyStatus())
                ? "⚠️ Taxminiy natija (noma’lum yoki kiritilmagan xarajatlar bor edi)."
                : "✅ Barcha xarajatlar kiritilgan edi.");
        sb.append("\n<i>Saqlangan natija o‘zgarmaydi. Engine versiyasi: ").append(e.getCalculationVersion()).append("</i>");
        return sb.toString();
    }

    private static void appendExpenses(StringBuilder sb, JsonNode ex) {
        for (ExpenseCategory c : ExpenseCategory.values()) {
            JsonNode x = ex.path(c.code());
            String status = x.path("status").asText("NOT_SET");
            sb.append("− ").append(CalcModel.label(c));
            if ("PERCENT_OF_SALE_PRICE".equals(x.path("type").asText())) {
                sb.append(" (").append(Money.formatPercent(new BigDecimal(x.path("value").asText("0")))).append("%)");
            }
            sb.append(": ");
            switch (status) {
                case "KNOWN", "ZERO" -> sb.append(Money.format(new BigDecimal(x.path("amount").asText("0"))));
                case "UNKNOWN" -> sb.append("<i>noma’lum</i>");
                default -> sb.append("<i>kiritilmagan</i>");
            }
            sb.append("\n");
        }
    }

    private static String fmt(JsonNode n, String key) {
        return n.hasNonNull(key) ? Money.format(new BigDecimal(n.path(key).asText())) : "—";
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    // ================================================================= recalc

    /** Katalogdagi joriy tannarx snapshotdagidan farq qilsa — manbani tanlash (UX-HIST-032). */
    private void recalcEntry(Ctx ctx, long id) {
        HistoryEntry e = history.get(ctx.businessId(), id);
        BigDecimal oldCost = new BigDecimal(read(e.getInputSnapshot()).path("cost").asText("0"));
        Optional<Product> product = e.getProductId() == null ? Optional.empty()
                : products.find(ctx.businessId(), e.getProductId());
        if (product.isPresent() && product.get().getUnitCost().compareTo(oldCost) != 0) {
            ctx.send(String.format(Texts.RECALC_COST_CHOICE, Money.format(oldCost), Money.format(product.get().getUnitCost())),
                    Kb.create().row(Kb.cb(Texts.BTN_OLD_COST, "h:rcc:" + id + ":o"), Kb.cb(Texts.BTN_NEW_COST, "h:rcc:" + id + ":c")));
            return;
        }
        dispatcher.requestStart(ctx, "RECALC:" + id + ":o");
    }

    void startRecalc(Ctx ctx, long id, boolean useCurrentCost) {
        HistoryEntry e = history.get(ctx.businessId(), id);
        Session s = ctx.session;
        String kind = e.getType() == Type.PROFIT ? Session.PROFIT : Session.PRICE;
        s.start(kind, CalcHandler.R);
        CalcModel.restoreDraft(mapper, s, e.getInputSnapshot(), e.getExpenseSnapshot());
        s.put("name", e.getProductNameSnapshot());
        s.put("ch", e.getChannelSnapshot());
        s.put("src", e.getId());
        if (e.getProductId() != null) {
            products.find(ctx.businessId(), e.getProductId()).ifPresent(p -> {
                s.put("pid", p.getId());
                if (useCurrentCost) s.put("cost", p.getUnitCost());
            });
        }
        s.markInput();
        ctx.send(Texts.RECALC_NOTE);
        calc.render(ctx);
    }
}
