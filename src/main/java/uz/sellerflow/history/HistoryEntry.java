package uz.sellerflow.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Qo'lda saqlangan hisoblash natijasi. Snapshot maydonlari (input/expense/output, version)
 * yaratilgandan keyin o'zgarmaydi — setter yo'q (UX-HIST-023). Faqat visibility metadata o'zgaradi.
 */
@Entity
@Table(name = "calculation_history")
public class HistoryEntry {

    public enum Type { PROFIT, PRICE }
    public enum Visibility { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "result_id", nullable = false, unique = true, updatable = false)
    private String resultId;

    @Column(name = "calculation_type", nullable = false, updatable = false)
    private String calculationType;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", updatable = false)
    private String productNameSnapshot;

    @Column(name = "channel_snapshot", nullable = false, updatable = false)
    private String channelSnapshot;

    @Column(nullable = false, updatable = false)
    private String currency = "UZS";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expense_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String expenseSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String outputSnapshot;

    @Column(name = "accuracy_status", nullable = false, updatable = false)
    private String accuracyStatus;

    @Column(name = "calculation_version", nullable = false, updatable = false)
    private String calculationVersion;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt = Instant.now();

    @Column(name = "visibility_status", nullable = false)
    private String visibilityStatus = Visibility.ACTIVE.name();

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "source_history_id", updatable = false)
    private Long sourceHistoryId;

    protected HistoryEntry() {}

    HistoryEntry(Long businessId, Long userId, String resultId, Type type, Long productId, String productNameSnapshot,
                 String channelSnapshot, String inputSnapshot, String expenseSnapshot, String outputSnapshot,
                 String accuracyStatus, String calculationVersion, Instant calculatedAt, Long sourceHistoryId) {
        this.businessId = businessId;
        this.userId = userId;
        this.resultId = resultId;
        this.calculationType = type.name();
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.channelSnapshot = channelSnapshot;
        this.inputSnapshot = inputSnapshot;
        this.expenseSnapshot = expenseSnapshot;
        this.outputSnapshot = outputSnapshot;
        this.accuracyStatus = accuracyStatus;
        this.calculationVersion = calculationVersion;
        this.calculatedAt = calculatedAt;
        this.sourceHistoryId = sourceHistoryId;
    }

    public Long getId() { return id; }
    public Long getBusinessId() { return businessId; }
    public Long getUserId() { return userId; }
    public String getResultId() { return resultId; }
    public Type getType() { return Type.valueOf(calculationType); }
    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getChannelSnapshot() { return channelSnapshot; }
    public String getCurrency() { return currency; }
    public String getInputSnapshot() { return inputSnapshot; }
    public String getExpenseSnapshot() { return expenseSnapshot; }
    public String getOutputSnapshot() { return outputSnapshot; }
    public String getAccuracyStatus() { return accuracyStatus; }
    public String getCalculationVersion() { return calculationVersion; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public Instant getSavedAt() { return savedAt; }
    public Visibility getVisibility() { return Visibility.valueOf(visibilityStatus); }
    public Instant getArchivedAt() { return archivedAt; }
    public Long getSourceHistoryId() { return sourceHistoryId; }

    void archive() {
        this.visibilityStatus = Visibility.ARCHIVED.name();
        this.archivedAt = Instant.now();
    }

    void restore() {
        this.visibilityStatus = Visibility.ACTIVE.name();
        this.archivedAt = null;
    }
}
