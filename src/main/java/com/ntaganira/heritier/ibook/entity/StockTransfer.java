/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : StockTransfer.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for moving stock between two locations
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfers")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true)
    private String transferNo;

    @Column(name = "from_warehouse_id", nullable = false)
    private Long fromWarehouseId;

    @Column(name = "from_warehouse_name", nullable = false)
    private String fromWarehouseName;

    @Column(name = "to_warehouse_id", nullable = false)
    private Long toWarehouseId;

    @Column(name = "to_warehouse_name", nullable = false)
    private String toWarehouseName;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransferStatus status = TransferStatus.DRAFT;

    @Column(name = "total_sent", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalSent = BigDecimal.ZERO;

    @Column(name = "total_received", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalReceived = BigDecimal.ZERO;

    @Column(name = "total_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    /** Value of the stock that never arrived, written off to cost of sales on completion. */
    @Column(name = "shortfall_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal shortfallValue = BigDecimal.ZERO;

    /** Only the shortfall write-off reaches the ledger; the move itself does not. */
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

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<StockTransferLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(StockTransferLine line) {
        line.setTransfer(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == TransferStatus.DRAFT;
    }

    @Transient
    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }

    @Transient
    public boolean isStopped() {
        return status == TransferStatus.CANCELLED || status == TransferStatus.VOID;
    }

    @Transient
    public boolean isCompletable() {
        return status == TransferStatus.DRAFT && !lines.isEmpty();
    }

    @Transient
    public boolean isShort() {
        return shortfallValue != null && shortfallValue.signum() > 0;
    }

    @Transient
    public BigDecimal getShortfallQuantity() {
        BigDecimal sent = totalSent == null ? BigDecimal.ZERO : totalSent;
        BigDecimal received = totalReceived == null ? BigDecimal.ZERO : totalReceived;
        BigDecimal diff = sent.subtract(received);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }
}
