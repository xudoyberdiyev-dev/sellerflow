package uz.sellerflow.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.ProductFileParser;
import uz.sellerflow.catalog.ProductImportService;
import uz.sellerflow.catalog.ProductService;
import uz.sellerflow.marketplace.UzumClient.UzumException;
import uz.sellerflow.marketplace.UzumService;
import uz.sellerflow.marketplace.UzumService.ConnectException;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;
import uz.sellerflow.telegram.TelegramGateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V2: 📥 import — Uzum ulanishi (kalit, sinxron, uzish), fayldan import, tannarxi yo'q mahsulotlarga tez tannarx kiritish.
 * Callback'lar: {@code mk:*}.
 */
@Component
public class MarketplaceHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceHandler.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Tashkent"));

    private final UzumService uzum;
    private final ProductImportService importer;
    private final ProductService products;
    private final JdbcTemplate jdbc;
    private final TelegramGateway tg;
    private final uz.sellerflow.channel.ChannelRateService rates;
    private final boolean async;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "uzum-sync");
        t.setDaemon(true);
        return t;
    });

    public MarketplaceHandler(UzumService uzum, ProductImportService importer, ProductService products, JdbcTemplate jdbc,
                              TelegramGateway tg, uz.sellerflow.channel.ChannelRateService rates,
                              @Value("${sellerflow.uzum.async:true}") boolean async) {
        this.rates = rates;
        this.uzum = uzum;
        this.importer = importer;
        this.products = products;
        this.jdbc = jdbc;
        this.tg = tg;
        this.async = async;
    }

    @jakarta.annotation.PreDestroy
    void shutdown() { executor.shutdownNow(); }

    public long missingCostCount(long businessId) {
        Long n = jdbc.queryForObject("select count(*) from products where business_id = ? and status = 'ACTIVE' and unit_cost is null",
                Long.class, businessId);
        return n == null ? 0 : n;
    }

    // ================================================================= callbacks

    public void onCallback(Ctx ctx, String[] p) {
        String a = p.length > 1 ? p[1] : "";
        switch (a) {
            case "menu" -> showMenu(ctx);
            case "uz" -> showUzum(ctx);
            case "uzk" -> startToken(ctx);
            case "uzs" -> { ctx.clearSourceButtons(); manualSync(ctx); }
            case "uzd" -> ctx.send(Texts.UZUM_DISCONNECT_CONFIRM, Kb.create()
                    .row(Kb.cb(Texts.BTN_UZUM_DISCONNECT_YES, "mk:uzd2"), Kb.cb(Texts.BTN_CANCEL, "mk:uz")));
            case "uzd2" -> {
                ctx.clearSourceButtons();
                uzum.disconnect(ctx.businessId());
                ctx.send(Texts.UZUM_DISCONNECTED);
                showUzum(ctx);
            }
            case "file" -> ctx.send(Texts.IMPORT_FILE_HELP, Kb.create().row(Kb.cb(Texts.BTN_BACK, "mk:menu")));
            case "cost" -> startCost(ctx);
            case "leak" -> showLeaks(ctx);
            case "cs" -> skipCost(ctx, p.length > 2 ? p[2] : null);
            case "x" -> {
                ctx.clearSourceButtons();
                if (ctx.session.isFlow(Session.MK_TOKEN) || ctx.session.isFlow(Session.MK_COST)) ctx.session.clear();
                ctx.sendMenu(Texts.CANCELLED);
            }
            default -> ctx.send(Texts.STALE);
        }
    }

    void showMenu(Ctx ctx) {
        long missing = missingCostCount(ctx.businessId());
        Kb kb = Kb.create()
                .row(Kb.cb(Texts.BTN_IMPORT_UZUM, "mk:uz"))
                .row(Kb.cb(Texts.BTN_IMPORT_FILE, "mk:file"));
        if (missing > 0) kb.row(Kb.cb(String.format(Texts.BTN_MISSING_COST, missing), "mk:cost"));
        kb.row(Kb.cb(Texts.BTN_BACK, "p:l:A:0"));
        ctx.send(Texts.IMPORT_MENU, kb);
    }

    void showUzum(Ctx ctx) {
        if (!uzum.enabled()) {
            ctx.send(Texts.UZUM_DISABLED, Kb.create().row(Kb.cb(Texts.BTN_BACK, "mk:menu")));
            return;
        }
        var c = uzum.connection(ctx.businessId());
        if (c.isEmpty()) {
            ctx.send(Texts.UZUM_HOWTO, Kb.create()
                    .row(Kb.cb(Texts.BTN_UZUM_ENTER_KEY, "mk:uzk"))
                    .row(Kb.cb(Texts.BTN_BACK, "mk:menu")));
            return;
        }
        var x = c.get();
        StringBuilder sb = new StringBuilder("🟣 <b>Uzum ulangan</b>\n\n");
        sb.append("🔑 Kalit: <code>").append(Ctx.esc(x.tokenHint())).append("</code>\n");
        sb.append("🏪 Do‘konlar: ").append(x.shopIds().size()).append(" ta\n");
        sb.append("🔄 Oxirgi yangilanish: ").append(x.lastSyncAt() == null ? "hali yo‘q" : TS.format(x.lastSyncAt()));
        if (x.lastSyncCount() != null) sb.append(" · ").append(x.lastSyncCount()).append(" ta SKU");
        sb.append("\n");
        if (x.syncStartedAt() != null && x.syncStartedAt().isAfter(Instant.now().minus(Duration.ofMinutes(30)))) {
            sb.append("⏳ Hozir yangilanmoqda…\n");
        }
        if (x.lastError() != null) sb.append("⚠️ Oxirgi xato: ").append(errorText(x.lastError())).append("\n");
        sb.append("\n").append(Texts.UZUM_CONNECTED_NOTE);
        Kb kb = Kb.create().row(Kb.cb(Texts.BTN_UZUM_SYNC, "mk:uzs"));
        long missing = missingCostCount(ctx.businessId());
        if (missing > 0) kb.row(Kb.cb(String.format(Texts.BTN_MISSING_COST, missing), "mk:cost"));
        kb.row(Kb.cb(Texts.BTN_UZUM_NEW_KEY, "mk:uzk"), Kb.cb(Texts.BTN_UZUM_DISCONNECT, "mk:uzd"))
                .row(Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0"), Kb.cb(Texts.BTN_BACK, "mk:menu"));
        ctx.send(sb.toString(), kb);
    }

    private void startToken(Ctx ctx) {
        if (!uzum.enabled()) {
            showUzum(ctx);
            return;
        }
        ctx.clearSourceButtons();
        ctx.session.start(Session.MK_TOKEN, "T");
        ctx.send(Texts.UZUM_ASK_KEY, Kb.create().row(Kb.cb(Texts.BTN_CANCEL, "mk:x")));
    }

    /** MK_TOKEN oqimidagi matn: kalit xabari darhol o'chiriladi, kalit sessiyaga yozilmaydi. */
    public void onText(Ctx ctx, String text) {
        if (ctx.session.isFlow(Session.MK_COST)) {
            onCostText(ctx, text);
            return;
        }
        ctx.deleteIncoming();
        ctx.session.clear();
        ctx.send(Texts.UZUM_CHECKING);
        try {
            var shops = uzum.connect(ctx.businessId(), text);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(5, shops.size()); i++) names.append("\n• ").append(Ctx.esc(shops.get(i).name()));
            if (shops.size() > 5) names.append("\n… va yana ").append(shops.size() - 5).append(" ta");
            ctx.send(String.format(Texts.UZUM_CONNECTED, shops.size(), names));
            startSync(ctx);
        } catch (ConnectException e) {
            ctx.send(switch (e.error()) {
                case INVALID_TOKEN -> Texts.UZUM_ERR_TOKEN;
                case NO_SHOPS -> Texts.UZUM_ERR_NO_SHOPS;
                case WAF -> Texts.UZUM_ERR_WAF;
                case QUOTA -> Texts.UZUM_ERR_QUOTA;
                case DISABLED -> Texts.UZUM_DISABLED;
                case UNAVAILABLE -> Texts.UZUM_ERR_UNAVAILABLE;
            }, Kb.create().row(Kb.cb(Texts.BTN_UZUM_ENTER_KEY, "mk:uzk"), Kb.cb(Texts.BTN_BACK, "mk:menu")));
        }
    }

    private void manualSync(Ctx ctx) {
        var c = uzum.connection(ctx.businessId());
        if (c.isEmpty()) {
            showUzum(ctx);
            return;
        }
        var next = uzum.manualSyncAllowedAt(ctx.businessId());
        if (next.isPresent()) {
            long min = Math.max(1, (Duration.between(Instant.now(), next.get()).toSeconds() + 59) / 60);
            ctx.send(String.format(Texts.UZUM_SYNC_COOLDOWN, min), Kb.create().row(Kb.cb(Texts.BTN_BACK, "mk:uz")));
            return;
        }
        startSync(ctx);
    }

    private void startSync(Ctx ctx) {
        long businessId = ctx.businessId();
        long chatId = ctx.chatId;
        ctx.send(Texts.UZUM_SYNC_STARTED);
        Runnable job = () -> runSync(businessId, chatId);
        if (async) executor.submit(job); else job.run();
    }

    private void runSync(long businessId, long chatId) {
        String msg;
        Kb kb = Kb.create();
        try {
            var r = uzum.sync(businessId);
            if (r.isEmpty()) {
                msg = Texts.UZUM_SYNC_RUNNING;
            } else {
                var x = r.get();
                long missing = missingCostCount(businessId);
                msg = String.format(Texts.UZUM_SYNC_DONE, x.shops(), x.skus(), x.created(), x.updated())
                        + (missing > 0 ? String.format(Texts.UZUM_SYNC_MISSING_COST, missing) : "");
                if (missing > 0) kb.row(Kb.cb(String.format(Texts.BTN_MISSING_COST, missing), "mk:cost"));
            }
        } catch (UzumException e) {
            msg = "⚠️ " + errorText(e.failure().name());
        } catch (RuntimeException e) {
            log.warn("Uzum sync business={} failed: {}", businessId, e.getClass().getSimpleName());
            msg = Texts.UZUM_ERR_UNAVAILABLE;
        }
        kb.row(Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0"), Kb.cb(Texts.BTN_IMPORT_UZUM, "mk:uz"));
        try {
            tg.sendMessage(chatId, msg, kb.build());
        } catch (RuntimeException e) {
            log.warn("Uzum sync result not delivered: {}", e.getClass().getSimpleName());
        }
    }

    static String errorText(String code) {
        return switch (code) {
            case "INVALID_TOKEN" -> Texts.UZUM_ERR_TOKEN;
            case "WAF_BLOCKED" -> Texts.UZUM_ERR_WAF;
            case "QUOTA_EXHAUSTED" -> Texts.UZUM_ERR_QUOTA;
            default -> Texts.UZUM_ERR_UNAVAILABLE;
        };
    }

    // ================================================================= foyda yo'qotishlari

    private record LeakRow(String name, BigDecimal price, BigDecimal profit, BigDecimal margin, boolean partial) {}

    /**
     * V2 (TZ 3.8): faol mahsulotlar bo'yicha 1 dona foyda — reja/Uzum narxi, tannarx, Uzum komissiyasi va saqlangan
     * kanal stavkalari bilan. Zarar va minimal marjadan pastlari ko'rsatiladi.
     */
    void showLeaks(Ctx ctx) {
        long biz = ctx.businessId();
        var primary = ctx.business().getPrimaryChannel();
        var savedPrimary = rates.get(biz, primary).orElse(null);
        var savedUzum = rates.get(biz, uz.sellerflow.account.AccountEntities.SalesChannel.UZUM).orElse(null);
        BigDecimal min = ctx.business().getMinMarginPct();
        List<LeakRow> all = new java.util.ArrayList<>();
        int[] noData = {0};
        jdbc.query("""
                select name, unit_cost, planned_sale_price, commission_pct, external_source from products
                where business_id = ? and status = 'ACTIVE' order by id limit 5000""", rs -> {
            BigDecimal cost = rs.getBigDecimal(2), price = rs.getBigDecimal(3), comm = rs.getBigDecimal(4);
            if (cost == null || price == null || price.signum() <= 0) {
                noData[0]++;
                return;
            }
            boolean fromUzum = "UZUM".equals(rs.getString(5));
            var saved = fromUzum && savedUzum != null ? savedUzum : savedPrimary;
            java.util.Map<uz.sellerflow.calculation.CalcTypes.ExpenseCategory, uz.sellerflow.calculation.Expense> exp =
                    new java.util.EnumMap<>(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.class);
            for (var c : uz.sellerflow.calculation.CalcTypes.ExpenseCategory.values()) {
                exp.put(c, saved == null ? uz.sellerflow.calculation.Expense.notSet(c) : CalcModel.fromNode(c, saved.get(c.code())));
            }
            if (comm != null) exp.put(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.COMMISSION,
                    uz.sellerflow.calculation.Expense.percent(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.COMMISSION, comm.toPlainString()));
            if (exp.get(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.DISCOUNT).status()
                    == uz.sellerflow.calculation.CalcTypes.ExpenseStatus.NOT_SET) {
                exp.put(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.DISCOUNT,
                        uz.sellerflow.calculation.Expense.zero(uz.sellerflow.calculation.CalcTypes.ExpenseCategory.DISCOUNT));
            }
            try {
                var r = uz.sellerflow.calculation.CalculationEngine.profit(price, cost, exp);
                all.add(new LeakRow(rs.getString(1), price, r.profit(), r.marginPct(),
                        r.accuracy() == uz.sellerflow.calculation.CalcTypes.Accuracy.PARTIAL_ESTIMATE));
            } catch (IllegalArgumentException e) {
                noData[0]++;
            }
        }, biz);
        if (all.isEmpty()) {
            ctx.send(String.format(Texts.LEAKS_EMPTY, noData[0]), Kb.create()
                    .row(Kb.cb(Texts.BTN_IMPORT, "mk:menu"), Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0")));
            return;
        }
        all.sort(java.util.Comparator.comparing(LeakRow::margin));
        long losses = all.stream().filter(r -> r.profit().signum() < 0).count();
        long low = min == null ? 0 : all.stream().filter(r -> r.profit().signum() >= 0 && r.margin().compareTo(min) < 0).count();
        boolean partial = all.stream().anyMatch(LeakRow::partial);
        StringBuilder sb = new StringBuilder(String.format(Texts.LEAKS_HEAD, all.size(), losses));
        if (min != null) sb.append(String.format(Texts.LEAKS_LOW, Money.formatPercent(min), low));
        if (noData[0] > 0) sb.append(String.format(Texts.LEAKS_NODATA, noData[0]));
        sb.append("\n");
        int shown = 0;
        for (LeakRow r : all) {
            boolean bad = r.profit().signum() < 0 || (min != null && r.margin().compareTo(min) < 0);
            if (!bad && shown >= 5) break;
            if (shown >= 15) break;
            sb.append(r.profit().signum() < 0 ? "🔻 " : bad ? "⚠️ " : "✅ ")
                    .append(Ctx.esc(uz.sellerflow.common.TextUtil.truncate(r.name(), 40, "…")))
                    .append("\n   narx ").append(Money.format(r.price())).append(" · foyda <b>").append(Money.format(r.profit()))
                    .append("</b> · marja ").append(Money.formatPercent(r.margin())).append("%\n");
            shown++;
        }
        if (partial) sb.append("\n").append(savedPrimary == null && savedUzum == null ? Texts.LEAKS_PARTIAL_NORATES : Texts.LEAKS_PARTIAL);
        sb.append("\n\n").append(Texts.DISCLAIMER);
        Kb kb = Kb.create();
        long missing = missingCostCount(biz);
        if (missing > 0) kb.row(Kb.cb(String.format(Texts.BTN_MISSING_COST, missing), "mk:cost"));
        kb.row(Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0"));
        ctx.send(sb.toString(), kb);
    }

    // ================================================================= fayldan import

    public void onDocument(Ctx ctx, Tg.Document doc) {
        if (doc.fileSize() != null && doc.fileSize() > ProductFileParser.MAX_FILE_BYTES) {
            ctx.send(Texts.IMPORT_ERR_SIZE);
            return;
        }
        String name = doc.fileName() == null ? "" : doc.fileName().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".csv") || name.endsWith(".xlsx") || name.endsWith(".txt") || name.endsWith(".xls"))) {
            ctx.send(Texts.IMPORT_ERR_TYPE, Kb.create().row(Kb.cb(Texts.BTN_IMPORT_FILE, "mk:file")));
            return;
        }
        byte[] data;
        try {
            data = tg.downloadFile(doc.fileId(), ProductFileParser.MAX_FILE_BYTES);
        } catch (IllegalArgumentException e) {
            ctx.send(Texts.IMPORT_ERR_SIZE);
            return;
        } catch (RuntimeException e) {
            log.warn("Import download failed: {}", e.getClass().getSimpleName());
            ctx.send(Texts.IMPORT_ERR_DOWNLOAD);
            return;
        }
        ProductFileParser.Parsed parsed;
        try {
            parsed = ProductFileParser.parse(doc.fileName(), data);
        } catch (ProductFileParser.ParseException e) {
            ctx.send(switch (e.getMessage()) {
                case "XLS_UNSUPPORTED" -> Texts.IMPORT_ERR_XLS;
                case "NO_HEADER" -> Texts.IMPORT_ERR_HEADER;
                case "TOO_MANY_ROWS" -> String.format(Texts.IMPORT_ERR_ROWS, ProductImportService.MAX_ROWS);
                case "FILE_TOO_LARGE" -> Texts.IMPORT_ERR_SIZE;
                default -> Texts.IMPORT_ERR_READ;
            }, Kb.create().row(Kb.cb(Texts.BTN_IMPORT_FILE, "mk:file")));
            return;
        }
        var r = importer.upsert(ctx.businessId(), ProductImportService.Source.FILE, parsed.rows());
        long missing = missingCostCount(ctx.businessId());
        StringBuilder cols = new StringBuilder();
        parsed.columns().forEach((k, v) -> cols.append("\n• ").append(colLabel(k)).append(" ← «").append(Ctx.esc(v)).append("»"));
        Kb kb = Kb.create();
        if (missing > 0) kb.row(Kb.cb(String.format(Texts.BTN_MISSING_COST, missing), "mk:cost"));
        kb.row(Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0"));
        ctx.send(String.format(Texts.IMPORT_DONE, parsed.dataRows(), r.created(), r.updated(), r.skipped(), cols), kb);
    }

    private static String colLabel(String k) {
        return switch (k) {
            case "NAME" -> "Nomi";
            case "SKU" -> "SKU / artikul";
            case "BARCODE" -> "Shtrix-kod";
            case "COST" -> "Tannarx";
            case "PRICE" -> "Sotish narxi";
            default -> k;
        };
    }

    // ================================================================= tannarxni tez kiritish

    private void startCost(Ctx ctx) {
        ctx.clearSourceButtons();
        ctx.session.start(Session.MK_COST, "C");
        nextCost(ctx);
    }

    private void nextCost(Ctx ctx) {
        Session s = ctx.session;
        long after = s.lng("after") == null ? 0 : s.lng("after");
        List<Object[]> rows = jdbc.query("""
                select id, name, planned_sale_price, commission_pct, stock_qty from products
                where business_id = ? and status = 'ACTIVE' and unit_cost is null and id > ? order by id limit 1""",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getObject(5)},
                ctx.businessId(), after);
        long left = missingCostCount(ctx.businessId());
        if (rows.isEmpty()) {
            s.clear();
            ctx.send(left == 0 ? Texts.COST_ALL_DONE : String.format(Texts.COST_SKIPPED_LEFT, left),
                    Kb.create().row(Kb.cb(Texts.BTN_PRODUCTS_LIST, "p:l:A:0")));
            return;
        }
        Object[] r = rows.get(0);
        long id = (Long) r[0];
        s.put("pid", id);
        StringBuilder sb = new StringBuilder(String.format(Texts.COST_ASK_HEAD, left));
        sb.append("📦 <b>").append(Ctx.esc((String) r[1])).append("</b>\n");
        if (r[2] != null) sb.append("🏷 Narx: ").append(Money.format((BigDecimal) r[2])).append(" so‘m\n");
        if (r[3] != null) sb.append("🟣 Uzum komissiyasi: ").append(Money.formatPercent((BigDecimal) r[3])).append("%\n");
        if (r[4] != null) sb.append("📦 Qoldiq: ").append(r[4]).append(" dona\n");
        sb.append("\n").append(Texts.COST_ASK);
        ctx.send(sb.toString(), Kb.create()
                .row(Kb.cb(Texts.BTN_COST_SKIP, "mk:cs:" + id), Kb.cb(Texts.BTN_COST_STOP, "mk:x")));
    }

    private void skipCost(Ctx ctx, String id) {
        Session s = ctx.session;
        if (!s.isFlow(Session.MK_COST) || id == null || !id.equals(String.valueOf(s.lng("pid")))) {
            ctx.send(Texts.STALE);
            return;
        }
        ctx.clearSourceButtons();
        s.put("after", Long.parseLong(id));
        nextCost(ctx);
    }

    private void onCostText(Ctx ctx, String text) {
        Session s = ctx.session;
        Long pid = s.lng("pid");
        var v = Money.parse(text);
        if (pid == null) {
            s.clear();
            ctx.sendMenu(Texts.USE_MENU);
            return;
        }
        if (v.isEmpty() || v.get().signum() < 0 || v.get().compareTo(Money.MAX) > 0) {
            ctx.send(Texts.ERR_NUMBER);
            return;
        }
        int n = jdbc.update("""
                update products set unit_cost = ?, version = version + 1, updated_at = now()
                where id = ? and business_id = ? and unit_cost is null""", Money.scale(v.get()), pid, ctx.businessId());
        if (n > 0) ctx.send(String.format(Texts.COST_SAVED, Money.format(v.get())));
        s.put("after", pid);
        nextCost(ctx);
    }
}
