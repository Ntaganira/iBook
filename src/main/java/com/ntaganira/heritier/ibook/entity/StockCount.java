/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : StockCount.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a physical stock count at one location
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.StockCountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_counts")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_no", nullable = false, unique = true)
    private String countNo;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", nullable = false)
    private String warehouseName;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Column(name = "counted_by")
    private String countedBy;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StockCountStatus status = StockCountStatus.DRAFT;

    @Column(name = "lines_counted", nullable = false)
    @Builder.Default
    private int linesCounted = 0;

    /** Value of everything found over the books, gross of any shortages. */
    @Column(name = "surplus_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal surplusValue = BigDecimal.ZERO;

    @Column(name = "shortage_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal shortageValue = BigDecimal.ZERO;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "reversal_journal_entry_id")
    private Long reversalJournalEntryId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<StockCountLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(StockCountLine line) {
        line.setStockCount(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == StockCountStatus.DRAFT;
    }

    @Transient
    public boolean isCompleted() {
        return status == StockCountStatus.COMPLETED;
    }

    @Transient
    public boolean isStopped() {
        return status == StockCountStatus.CANCELLED || status == StockCountStatus.VOID;
    }

    @Transient
    public boolean isCompletable() {
        return status == StockCountStatus.DRAFT && countedLineCount() > 0;
    }

    @Transient
    public int countedLineCount() {
        int n = 0;
        for (StockCountLine l : lines) {
            if (l.isCounted()) {
                n++;
            }
        }
        return n;
    }

    @Transient
    public int getVarianceLineCount() {
        int n = 0;
        for (StockCountLine l : lines) {
            if (l.isCounted() && l.getVariance().signum() != 0) {
                n++;
            }
        }
        return n;
    }

    /** Surplus less shortage: what the count did to the value of stock on hand. */
    @Transient
    public BigDecimal getNetVarianceValue() {
        BigDecimal up = surplusValue == null ? BigDecimal.ZERO : surplusValue;
        BigDecimal down = shortageValue == null ? BigDecimal.ZERO : shortageValue;
        return up.subtract(down);
    }

    @Transient
    public boolean isAccurate() {
        return status == StockCountStatus.COMPLETED && getVarianceLineCount() == 0;
    }
}
