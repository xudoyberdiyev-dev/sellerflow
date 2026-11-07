package uz.sellerflow.bot;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.Product;
import uz.sellerflow.catalog.ProductService;
import uz.sellerflow.catalog.ProductService.CreateCommand;
import uz.sellerflow.catalog.ProductService.Field;
import uz.sellerflow.common.Errors;
import uz.sellerflow.plan.PlanService;
import uz.sellerflow.telegram.Tg;
import uz.sellerflow.telegram.Tg.Kb;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 📦 Mahsulotlarim (SF-PRD-02.06): ro'yxat (10 ta/sahifa), qidiruv, detail, yaratish, maydon bo'yicha tahrir,
 * arxivlash/tiklash, katalogdan kalkulyatorga o'tish.
 */
@Component
public class ProductHandler {

    public static final String MODE_SEARCH = "PRODUCT_SEARCH";
    static final String PN = "PN", PC = "PC", PP = "PP", PS = "PS", PR = "PR", PE = "PE";

    private final ProductService products;
    private final PlanService plans;
    private BotDispatcher dispatcher;

    public ProductHandler(ProductService products, PlanService plans) {
        this.products = products;
        this.plans = plans;
    }

    void bind(BotDispatcher d) { this.dispatcher = d; }

    // ================================================================= list / detail

    public void showList(Ctx ctx, boolean archived, int page) {
        Product.Status st = archived ? Product.Status.ARCHIVED : Product.Status.ACTIVE;
        Page<Product> pg = products.list(ctx.businessId(), st, page);
        if (!archived && pg.getTotalElements() == 0) {
            ctx.send(Texts.PRODUCTS_EMPTY, Kb.create()
                    .row(Kb.cb(Texts.BTN_NEW_PRODUCT, "p:new"))
                    .row(Kb.cb(Texts.BTN_ARCHIVE_LIST, "p:l:R:0")));
            return;
        }
        String title = archived ? Texts.PRODUCTS_ARCHIVE_TITLE
                : String.format(Texts.PRODUCTS_TITLE, products.countActive(ctx.businessId()));
        if (archived && pg.getTotalElements() == 0) title += "\n\nArxiv bo‘sh.";
        Kb kb = Kb.create();
        for (Product p : pg.getContent()) kb.row(Kb.cb(itemLabel(p), "p:v:" + p.getId()));
        pager(kb, pg, "p:l:" + (archived ? "R" : "A") + ":");
        if (archived) {
            kb.row(Kb.cb(Texts.BTN_ACTIVE_LIST, "p:l:A:0"));
        } else {
            kb.row(Kb.cb(Texts.BTN_NEW_PRODUCT, "p:new"), Kb.cb(Texts.BTN_SEARCH, "p:q"));
            kb.row(Kb.cb(Texts.BTN_ARCHIVE_LIST, "p:l:R:0"));
        }
        ctx.send(title + pageInfo(pg), kb);
    }

    public void search(Ctx ctx, String query) {
        Page<Product> pg = products.search(ctx.businessId(), Product.Status.ACTIVE, query, 0);
        if (pg.isEmpty()) {
            ctx.send(String.format(Texts.PRODUCTS_SEARCH_EMPTY, Ctx.esc(query)), Kb.create()
                    .row(Kb.cb(Texts.BTN_SEARCH, "p:q"), Kb.cb(Texts.BTN_LIST, "p:l:A:0")));
            return;
        }
        Kb kb = Kb.create();
        for (Product p : pg.getContent()) kb.row(Kb.cb(itemLabel(p), "p:v:" + p.getId()));
        kb.row(Kb.cb(Texts.BTN_LIST, "p:l:A:0"));
        ctx.send(String.format(Texts.PRODUCTS_SEARCH_TITLE, Ctx.esc(query))
                + (pg.getTotalElements() > pg.getNumberOfElements() ? "\n(birinchi " + pg.getNumberOfElements() + " ta)" : ""), kb);
    }

    private void showDetail(Ctx ctx, long id) {
        Product p = products.get(ctx.businessId(), id);
        Kb kb = Kb.create();
        if (p.getStatus() == Product.Status.ACTIVE) {
            kb.row(Kb.cb(Texts.BTN_PROFIT, "p:calc:" + id), Kb.cb(Texts.BTN_PRICE, "p:price:" + id));
            kb.row(Kb.cb(Texts.BTN_EDIT, "p:e:" + id), Kb.cb(Texts.BTN_ARCHIVE, "p:a:" + id));
        } else {
            kb.row(Kb.cb(Texts.BTN_RESTORE, "p:r:" + id));
        }
        kb.row(Kb.cb(Texts.BTN_LIST, "p:l:" + (p.getStatus() == Product.Status.ACTIVE ? "A" : "R") + ":0"));
        ctx.send(details(p), kb);
    }

    static String details(Product p) {
        return "📦 <b>" + Ctx.esc(p.getName()) + "</b>\n\n"
                + "💵 Tannarx: <b>" + Money.format(p.getUnitCost()) + " so‘m</b>\n"
                + "🏷 Reja narxi: " + (p.getPlannedSalePrice() == null ? "kiritilmagan" : Money.format(p.getPlannedSalePrice()) + " so‘m") + "\n"
                + "🔖 SKU: " + (p.getSku() == null ? "—" : Ctx.esc(p.getSku())) + "\n"
                + "Holat: " + (p.getStatus() == Product.Status.ACTIVE ? "faol" : "arxivda");
    }

    private static String itemLabel(Product p) {
        String name = uz.sellerflow.common.TextUtil.truncate(p.getName(), 40, "…");
        return name + " — " + Money.format(p.getUnitCost()) + " so‘m";
    }

    static void pager(Kb kb, Page<?> pg, String prefix) {
        if (pg.getTotalPages() <= 1) return;
        Tg.InlineButton prev = pg.hasPrevious() ? Kb.cb("⬅️", prefix + (pg.getNumber() - 1)) : null;
        Tg.InlineButton next = pg.hasNext() ? Kb.cb("➡️", prefix + (pg.getNumber() + 1)) : null;
        if (prev != null && next != null) kb.row(prev, next);
        else if (prev != null) kb.row(prev);
        else if (next != null) kb.row(next);
    }

    static String pageInfo(Page<?> pg) {
        return pg.getTotalPages() > 1 ? "\nSahifa " + (pg.getNumber() + 1) + " / " + pg.getTotalPages() : "";
    }

    // ================================================================= callbacks

    public void onCallback(Ctx ctx, String[] p) {
        if ("pw".equals(p[0])) {
            onFlowCallback(ctx, p);
            return;
        }
        String a = p.length > 1 ? p[1] : "";
        switch (a) {
            case "l" -> showList(ctx, "R".equals(p[2]), Integer.parseInt(p[3]));
            case "v" -> showDetail(ctx, Long.parseLong(p[2]));
            case "new" -> {
                if (!plans.isAllowed(ctx.business().getPlanCode(), "PRODUCT_CATALOG", products.countActive(ctx.businessId()))) {
                    ctx.send(Texts.FEATURE_LIMITED, Kb.create()
                            .row(Kb.cb(Texts.BTN_TARIFFS_INLINE, "t:show"), Kb.cb(Texts.BTN_MAIN, "m")));
                    return;
                }
                dispatcher.requestStart(ctx, "PCREATE");
            }
            case "q" -> {
                ctx.session.uiMode(MODE_SEARCH);
                ctx.send(Texts.ASK_PRODUCT_SEARCH);
            }
            case "a" -> {
                Product pr = products.get(ctx.businessId(), Long.parseLong(p[2]));
                ctx.send(String.format(Texts.ARCHIVE_CONFIRM, Ctx.esc(pr.getName())), Kb.create()
                        .row(Kb.cb(Texts.BTN_ARCHIVE, "p:ac:" + pr.getId()), Kb.cb(Texts.BTN_CANCEL, "p:v:" + pr.getId())));
            }
            case "ac" -> {
                ctx.clearSourceButtons();
                products.archive(ctx.businessId(), Long.parseLong(p[2]));
                ctx.send(Texts.ARCHIVED);
                showList(ctx, false, 0);
            }
            case "r" -> {
                ctx.clearSourceButtons();
                Product pr = products.restore(ctx.businessId(), Long.parseLong(p[2]));
                ctx.send(Texts.RESTORED);
                showDetail(ctx, pr.getId());
            }
            case "e" -> {
                Product pr = products.get(ctx.businessId(), Long.parseLong(p[2]));
                String base = pr.getId() + ":" + pr.getVersion();
                Kb kb = Kb.create()
                        .row(Kb.cb(Texts.BTN_F_NAME, "p:ef:" + base + ":N"), Kb.cb(Texts.BTN_F_COST, "p:ef:" + base + ":C"))
                        .row(Kb.cb(Texts.BTN_F_PLANNED, "p:ef:" + base + ":P"), Kb.cb(Texts.BTN_F_SKU, "p:ef:" + base + ":S"));
                if (pr.getPlannedSalePrice() != null) kb.row(Kb.cb(Texts.BTN_CLEAR_PLANNED, "p:ec:" + base + ":P"));
                if (pr.getSku() != null) kb.row(Kb.cb(Texts.BTN_CLEAR_SKU, "p:ec:" + base + ":S"));
                kb.row(Kb.cb(Texts.BTN_BACK, "p:v:" + pr.getId()));
                ctx.send(details(pr) + "\n\n" + Texts.EDIT_WHICH, kb);
            }
            case "ef" -> dispatcher.requestStart(ctx, "PEDIT:" + p[2] + ":" + p[3] + ":" + p[4]);
            case "ec" -> {
                ctx.clearSourceButtons();
                long id = Long.parseLong(p[2]);
                Field f = "P".equals(p[4]) ? Field.PLANNED_PRICE : Field.SKU;
                products.update(ctx.businessId(), id, Integer.parseInt(p[3]), f, null);
                ctx.send(Texts.FIELD_UPDATED);
                showDetail(ctx, id);
            }
            case "calc" -> dispatcher.requestStart(ctx, "PPROFIT:" + Long.parseLong(p[2]));
            case "price" -> dispatcher.requestStart(ctx, "PPRICE:" + Long.parseLong(p[2]));
            default -> ctx.send(Texts.STALE);
        }
    }

    // ================================================================= create / edit flows

    void startCreate(Ctx ctx) {
        ctx.session.start(Session.PRODUCT_CREATE, PN);
        render(ctx);
    }

    void startEdit(Ctx ctx, long productId, int version, String field) {
        Product p = products.get(ctx.businessId(), productId);
        if (p.getVersion() != version) throw new Errors.Conflict("version");
        Session s = ctx.session;
        s.start(Session.PRODUCT_EDIT, PE);
        s.put("pid", productId);
        s.data().put("ver", version);
        s.put("field", field);
        render(ctx);
    }

    public void render(Ctx ctx) {
        Session s = ctx.session;
        int rev = s.revision();
        String step = s.step();
        Kb nav = Kb.create();
        switch (step) {
            case PN -> ctx.send(Texts.ASK_PRODUCT_NAME, nav.row(b(rev, PN, Texts.BTN_CANCEL, "cancel")));
            case PC -> ctx.send(Texts.ASK_PRODUCT_COST, nav.row(b(rev, PC, Texts.BTN_BACK, "back"), b(rev, PC, Texts.BTN_CANCEL, "cancel")));
            case PP -> ctx.send(Texts.ASK_PRODUCT_PLANNED, nav.row(b(rev, PP, Texts.BTN_SKIP, "skip"))
                    .row(b(rev, PP, Texts.BTN_BACK, "back"), b(rev, PP, Texts.BTN_CANCEL, "cancel")));
            case PS -> ctx.send(Texts.ASK_PRODUCT_SKU, nav.row(b(rev, PS, Texts.BTN_SKIP, "skip"))
                    .row(b(rev, PS, Texts.BTN_BACK, "back"), b(rev, PS, Texts.BTN_CANCEL, "cancel")));
            case PR -> {
                String summary = "📦 Nomi: <b>" + Ctx.esc(s.str("name")) + "</b>\n"
                        + "💵 Tannarx: <b>" + Money.format(s.dec("cost")) + " so‘m</b>\n"
                        + "🏷 Reja narxi: " + (s.str("planned") == null ? "kiritilmagan" : Money.format(s.dec("planned")) + " so‘m") + "\n"
                        + "🔖 SKU: " + (s.str("sku") == null ? "—" : Ctx.esc(s.str("sku")));
                if (products.activeNameExists(ctx.businessId(), s.str("name"), null)) summary += Texts.DUP_NAME_WARNING;
                ctx.send(String.format(Texts.PRODUCT_REVIEW, summary), Kb.create()
                        .row(b(rev, PR, Texts.BTN_SAVE, "save"))
                        .row(b(rev, PR, Texts.BTN_F_NAME, "e:PN"), b(rev, PR, Texts.BTN_F_COST, "e:PC"))
                        .row(b(rev, PR, Texts.BTN_F_PLANNED, "e:PP"), b(rev, PR, Texts.BTN_F_SKU, "e:PS"))
                        .row(b(rev, PR, Texts.BTN_CANCEL, "cancel")));
            }
            case PE -> {
                String f = s.str("field");
                String prompt = switch (f) {
                    case "N" -> Texts.ASK_PRODUCT_NAME;
                    case "C" -> Texts.ASK_PRODUCT_COST;
                    case "P" -> Texts.ASK_PRODUCT_PLANNED;
                    default -> Texts.ASK_PRODUCT_SKU;
                };
                ctx.send(prompt, nav.row(b(rev, PE, Texts.BTN_CANCEL, "cancel")));
            }
            default -> ctx.sendMenu(Texts.MAIN_MENU);
        }
    }

    public void onText(Ctx ctx, String text) {
        Session s = ctx.session;
        long businessId = ctx.businessId();
        try {
            switch (s.step()) {
                case PN -> {
                    s.put("name", ProductService.validateName(text));
                    next(ctx, PC);
                }
                case PC -> {
                    Optional<BigDecimal> v = Money.parse(text);
                    if (v.isEmpty()) { ctx.send(Texts.ERR_NUMBER); return; }
                    s.put("cost", ProductService.validateUnitCost(v.get()));
                    next(ctx, PP);
                }
                case PP -> {
                    Optional<BigDecimal> v = Money.parse(text);
                    if (v.isEmpty()) { ctx.send(Texts.ERR_NUMBER); return; }
                    s.put("planned", ProductService.validatePlannedPrice(v.get()));
                    next(ctx, PS);
                }
                case PS -> {
                    String sku = ProductService.validateSkuFormat(text);
                    if (products.isSkuTaken(businessId, sku, null)) throw new Errors.Validation("SKU_TAKEN");
                    s.put("sku", sku);
                    next(ctx, PR);
                }
                case PE -> applyEdit(ctx, text);
                default -> ctx.send(Texts.EXPECT_BUTTON);
            }
        } catch (Errors.Validation e) {
            ctx.send(BotDispatcher.validationMessage(e.code()));
        }
    }

    private void next(Ctx ctx, String defaultNext) {
        Session s = ctx.session;
        s.markInput();
        if (s.data().path("fromReview").asBoolean(false)) {
            s.data().remove("fromReview");
            s.step(PR);
        } else {
            s.step(defaultNext);
        }
        render(ctx);
    }

    private void applyEdit(Ctx ctx, String text) {
        Session s = ctx.session;
        long id = s.lng("pid");
        int ver = s.data().path("ver").asInt();
        String f = s.str("field");
        Product updated;
        try {
            updated = doEdit(ctx, id, ver, f, text);
        } catch (Errors.Conflict e) {
            s.clear();
            ctx.send(Texts.ERR_CONFLICT);
            showDetail(ctx, id);
            return;
        }
        if (updated == null) return;
        s.clear();
        ctx.send(Texts.FIELD_UPDATED);
        showDetail(ctx, updated.getId());
    }

    private Product doEdit(Ctx ctx, long id, int ver, String f, String text) {
        Product updated;
        switch (f) {
            case "N" -> updated = products.update(ctx.businessId(), id, ver, Field.NAME, text);
            case "C", "P" -> {
                Optional<BigDecimal> v = Money.parse(text);
                if (v.isEmpty()) { ctx.send(Texts.ERR_NUMBER); return null; }
                updated = products.update(ctx.businessId(), id, ver, "C".equals(f) ? Field.UNIT_COST : Field.PLANNED_PRICE, v.get());
            }
            default -> updated = products.update(ctx.businessId(), id, ver, Field.SKU, text);
        }
        return updated;
    }

    private void onFlowCallback(Ctx ctx, String[] p) {
        Session s = ctx.session;
        if (p.length < 4 || !(s.isFlow(Session.PRODUCT_CREATE) || s.isFlow(Session.PRODUCT_EDIT))
                || !String.valueOf(s.revision()).equals(p[1]) || !p[2].equals(s.step())) {
            ctx.send(Texts.STALE);
            return;
        }
        s.resume();
        ctx.clearSourceButtons();
        String action = p[3];
        switch (action) {
            case "cancel" -> {
                Long pid = s.lng("pid");
                boolean edit = s.isFlow(Session.PRODUCT_EDIT);
                s.clear();
                ctx.send(Texts.CANCELLED);
                if (edit && pid != null) showDetail(ctx, pid); else showList(ctx, false, 0);
            }
            case "back" -> {
                s.step(switch (s.step()) {
                    case PC -> PN;
                    case PP -> PC;
                    case PS -> PP;
                    default -> s.step();
                });
                render(ctx);
            }
            case "skip" -> {
                if (PP.equals(s.step())) {
                    s.data().putNull("planned");
                    next(ctx, PS);
                } else if (PS.equals(s.step())) {
                    s.data().putNull("sku");
                    next(ctx, PR);
                }
            }
            case "e" -> {
                s.data().put("fromReview", true);
                s.step(p[4]);
                render(ctx);
            }
            case "save" -> {
                // Idempotentlik kaliti: shu jarayon revision'i — takror bosish ikkinchi mahsulot yaratmaydi
                String key = "u" + s.userId() + "-r" + s.revision();
                Product created = products.create(ctx.businessId(), new CreateCommand(s.str("name"), s.dec("cost"),
                        s.dec("planned"), s.str("sku")), key);
                s.clear();
                ctx.send(Texts.PRODUCT_SAVED);
                showDetail(ctx, created.getId());
            }
            default -> ctx.send(Texts.STALE);
        }
    }

    private static Tg.InlineButton b(int rev, String step, String text, String action) {
        return Kb.cb(text, "pw:" + rev + ":" + step + ":" + action);
    }
}
