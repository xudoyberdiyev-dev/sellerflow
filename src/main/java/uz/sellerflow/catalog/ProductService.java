package uz.sellerflow.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sellerflow.audit.AuditService;
import uz.sellerflow.audit.AuditService.Actor;
import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.Product.Status;
import uz.sellerflow.common.Errors;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mahsulotlar katalogi (SF-PRD-02.06). Har bir amal business_id bo'yicha tenant-scoped.
 * Boshqa tenant mahsuloti uchun ham «topilmadi» javobi beriladi (IDOR himoyasi).
 */
@Service
public class ProductService {

    public static final int PAGE_SIZE = 10;
    private static final Pattern SKU_PATTERN = Pattern.compile("^[\\p{L}\\p{N}._\\-/]{1,64}$");

    public interface ProductRepository extends JpaRepository<Product, Long> {
        Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

        Optional<Product> findByIdempotencyKey(String key);

        @Query("select p from Product p where p.businessId = ?1 and p.status = ?2 order by p.id desc")
        Page<Product> list(Long businessId, Status status, org.springframework.data.domain.Pageable pageable);

        @Query("""
                select p from Product p where p.businessId = ?1 and p.status = ?2
                  and (lower(p.name) like ?3 or lower(coalesce(p.sku, '')) like ?3)
                order by p.id desc""")
        Page<Product> search(Long businessId, Status status, String likeLower, org.springframework.data.domain.Pageable pageable);

        @Query("select count(p) > 0 from Product p where p.businessId = ?1 and lower(p.sku) = lower(?2) and (?3 is null or p.id <> ?3)")
        boolean skuTaken(Long businessId, String sku, Long exceptId);

        @Query("select count(p) > 0 from Product p where p.businessId = ?1 and lower(p.name) = lower(?2) and p.status = ?4 and (?3 is null or p.id <> ?3)")
        boolean nameExists(Long businessId, String name, Long exceptId, Status status);

        long countByBusinessIdAndStatus(Long businessId, Status status);
    }

    public record CreateCommand(String name, BigDecimal unitCost, BigDecimal plannedSalePrice, String sku) {}

    public enum Field { NAME, UNIT_COST, PLANNED_PRICE, SKU }

    private final ProductRepository repo;
    private final AuditService audit;

    public ProductService(ProductRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    // ------------------------------------------------------------ validation

    public static String validateName(String raw) {
        if (raw == null) throw new Errors.Validation("PRODUCT_NAME_REQUIRED");
        String t = raw.strip().replaceAll("\\s+", " ");
        int len = t.codePointCount(0, t.length());
        if (len < 1) throw new Errors.Validation("PRODUCT_NAME_REQUIRED");
        if (len > 120) throw new Errors.Validation("PRODUCT_NAME_LENGTH");
        return t;
    }

    public static BigDecimal validateUnitCost(BigDecimal c) {
        if (c == null) throw new Errors.Validation("UNIT_COST_REQUIRED");
        if (c.signum() < 0) throw new Errors.Validation("UNIT_COST_NEGATIVE");
        if (c.compareTo(Money.MAX) > 0) throw new Errors.Validation("VALUE_TOO_LARGE");
        return Money.scale(c);
    }

    public static BigDecimal validatePlannedPrice(BigDecimal p) {
        if (p == null) return null;
        if (p.signum() <= 0) throw new Errors.Validation("PLANNED_PRICE_NOT_POSITIVE");
        if (p.compareTo(Money.MAX) > 0) throw new Errors.Validation("VALUE_TOO_LARGE");
        return Money.scale(p);
    }

    public static String validateSkuFormat(String raw) {
        if (raw == null) return null;
        String t = raw.strip();
        if (t.isEmpty()) return null;
        if (!SKU_PATTERN.matcher(t).matches()) throw new Errors.Validation("SKU_FORMAT");
        return t;
    }

    @Transactional(readOnly = true)
    public boolean isSkuTaken(long businessId, String sku, Long exceptId) {
        return sku != null && repo.skuTaken(businessId, sku, exceptId);
    }

    @Transactional(readOnly = true)
    public boolean activeNameExists(long businessId, String name, Long exceptId) {
        return repo.nameExists(businessId, name, exceptId, Status.ACTIVE);
    }

    // ------------------------------------------------------------ commands

    /** Idempotent yaratish: bir xil kalit bilan takror chaqiruv ikkinchi mahsulot yaratmaydi (UX-PROD-015). */
    @Transactional
    public Product create(long businessId, CreateCommand cmd, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Product> existing = repo.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getBusinessId().equals(businessId)) throw new Errors.NotFound("product");
                return existing.get();
            }
        }
        String name = validateName(cmd.name());
        BigDecimal cost = validateUnitCost(cmd.unitCost());
        BigDecimal planned = validatePlannedPrice(cmd.plannedSalePrice());
        String sku = validateSkuFormat(cmd.sku());
        if (isSkuTaken(businessId, sku, null)) throw new Errors.Validation("SKU_TAKEN");
        Product p = repo.saveAndFlush(new Product(businessId, name, cost, planned, sku, idempotencyKey));
        audit.record(Actor.USER, null, businessId, "PRODUCT_CREATED", "PRODUCT", p.getId(), null);
        return p;
    }

    @Transactional(readOnly = true)
    public Product get(long businessId, long productId) {
        return repo.findByIdAndBusinessId(productId, businessId).orElseThrow(() -> new Errors.NotFound("product"));
    }

    @Transactional(readOnly = true)
    public Optional<Product> find(long businessId, long productId) {
        return repo.findByIdAndBusinessId(productId, businessId);
    }

    /**
     * Bitta maydonni tahrirlash. expectedVersion mos kelmasa — Conflict (UX-PROD-024).
     * value: NAME -> String, UNIT_COST/PLANNED_PRICE -> BigDecimal (PLANNED_PRICE null = tozalash), SKU -> String|null.
     */
    @Transactional
    public Product update(long businessId, long productId, int expectedVersion, Field field, Object value) {
        Product p = get(businessId, productId);
        if (p.getVersion() != expectedVersion) throw new Errors.Conflict("product version changed");
        switch (field) {
            case NAME -> p.setName(validateName((String) value));
            case UNIT_COST -> p.setUnitCost(validateUnitCost((BigDecimal) value));
            case PLANNED_PRICE -> p.setPlannedSalePrice(validatePlannedPrice((BigDecimal) value));
            case SKU -> {
                String sku = validateSkuFormat((String) value);
                if (isSkuTaken(businessId, sku, productId)) throw new Errors.Validation("SKU_TAKEN");
                p.setSku(sku);
            }
        }
        repo.saveAndFlush(p);
        audit.record(Actor.USER, null, businessId, "PRODUCT_UPDATED", "PRODUCT", productId, "field=" + field);
        return p;
    }

    /** ACTIVE -> ARCHIVED. Takror chaqiruv xavfsiz. Tarix o'zgarmaydi (UX-PROD-029). */
    @Transactional
    public Product archive(long businessId, long productId) {
        Product p = get(businessId, productId);
        if (p.getStatus() == Status.ARCHIVED) return p;
        p.archive();
        repo.saveAndFlush(p);
        audit.record(Actor.USER, null, businessId, "PRODUCT_ARCHIVED", "PRODUCT", productId, null);
        return p;
    }

    /** ARCHIVED -> ACTIVE, dublikat yaratmaydi. */
    @Transactional
    public Product restore(long businessId, long productId) {
        Product p = get(businessId, productId);
        if (p.getStatus() == Status.ACTIVE) return p;
        p.restore();
        repo.saveAndFlush(p);
        audit.record(Actor.USER, null, businessId, "PRODUCT_RESTORED", "PRODUCT", productId, null);
        return p;
    }

    @Transactional(readOnly = true)
    public Page<Product> list(long businessId, Status status, int page) {
        return repo.list(businessId, status, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(long businessId, Status status, String query, int page) {
        String q = query == null ? "" : query.strip().toLowerCase();
        String like = "%" + q.replace("\\", "").replace("%", "").replace("_", "") + "%";
        return repo.search(businessId, status, like, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public long countActive(long businessId) {
        return repo.countByBusinessIdAndStatus(businessId, Status.ACTIVE);
    }
}
