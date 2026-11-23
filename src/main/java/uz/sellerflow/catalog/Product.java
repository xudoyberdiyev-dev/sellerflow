package uz.sellerflow.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Oddiy per-unit katalog yozuvi. Ombor emas (SF-PRD-02.06). */
@Entity
@Table(name = "products")
public class Product {

    public enum Status { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String name;

    /** V2: import qilingan mahsulotda null bo'lishi mumkin (tannarx hali kiritilmagan). */
    @Column(name = "unit_cost", precision = 19, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "planned_sale_price", precision = 19, scale = 2)
    private BigDecimal plannedSalePrice;

    @Column(length = 64)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Version
    private int version;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "archived_at")
    private Instant archivedAt;

    // V2: marketplace'dan kelgan maydonlar (sinxron yozadi, bot faqat o'qiydi)
    @Column(name = "external_source", length = 16)
    private String externalSource;

    @Column(name = "external_sku_id")
    private Long externalSkuId;

    @Column(length = 64)
    private String barcode;

    @Column(name = "commission_pct", precision = 6, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "stock_qty")
    private Integer stockQty;

    @Column(name = "sold_qty")
    private Integer soldQty;

    @Column(name = "returned_qty")
    private Integer returnedQty;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected Product() {}

    public Product(Long businessId, String name, BigDecimal unitCost, BigDecimal plannedSalePrice, String sku,
                   String idempotencyKey) {
        this.businessId = businessId;
        this.name = name;
        this.unitCost = unitCost;
        this.plannedSalePrice = plannedSalePrice;
        this.sku = sku;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public Long getBusinessId() { return businessId; }
    public String getName() { return name; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getPlannedSalePrice() { return plannedSalePrice; }
    public String getSku() { return sku; }
    public Status getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getExternalSource() { return externalSource; }
    public Long getExternalSkuId() { return externalSkuId; }
    public String getBarcode() { return barcode; }
    public BigDecimal getCommissionPct() { return commissionPct; }
    public Integer getStockQty() { return stockQty; }
    public Integer getSoldQty() { return soldQty; }
    public Integer getReturnedQty() { return returnedQty; }
    public Instant getSyncedAt() { return syncedAt; }
    public boolean hasCost() { return unitCost != null; }
    public boolean isFromUzum() { return "UZUM".equals(externalSource); }

    void setName(String name) { this.name = name; touch(); }
    void setUnitCost(BigDecimal c) { this.unitCost = c; touch(); }
    void setPlannedSalePrice(BigDecimal p) { this.plannedSalePrice = p; touch(); }
    void setSku(String sku) { this.sku = sku; touch(); }

    void archive() {
        this.status = Status.ARCHIVED;
        this.archivedAt = Instant.now();
        touch();
    }

    void restore() {
        this.status = Status.ACTIVE;
        this.archivedAt = null;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }
}
