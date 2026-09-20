/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : AssetTransfer.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : JPA entity for moving a fixed asset between locations, custodians or accounts
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.AssetTransferStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "asset_transfers")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true)
    private String transferNo;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_no")
    private String assetNo;

    @Column(name = "asset_name")
    private String assetName;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "from_location_id")
    private Long fromLocationId;

    /** The name as it stood when the asset moved; renaming the location later does not rewrite it. */
    @Column(name = "from_location")
    private String fromLocation;

    @Column(name = "to_location_id")
    private Long toLocationId;

    @Column(name = "to_location")
    private String toLocation;

    @Column(name = "from_custodian")
    private String fromCustodian;

    @Column(name = "to_custodian")
    private String toCustodian;

    @Column(name = "from_asset_account_id")
    private Long fromAssetAccountId;

    @Column(name = "from_asset_account_code")
    private String fromAssetAccountCode;

    @Column(name = "to_asset_account_id")
    private Long toAssetAccountId;

    @Column(name = "to_asset_account_code")
    private String toAssetAccountCode;

    @Column(name = "from_accumulated_account_id")
    private Long fromAccumulatedAccountId;

    @Column(name = "from_accumulated_account_code")
    private String fromAccumulatedAccountCode;

    @Column(name = "to_accumulated_account_id")
    private Long toAccumulatedAccountId;

    @Column(name = "to_accumulated_account_code")
    private String toAccumulatedAccountCode;

    /**
     * Recorded rather than derived: the asset's accounts can be edited afterwards, and the history
     * has to keep saying whether this transfer was the thing that moved value between them.
     */
    @Column(name = "reclassified", nullable = false)
    @Builder.Default
    private boolean reclassified = false;

    @Column(name = "cost_at_transfer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal costAtTransfer = BigDecimal.ZERO;

    @Column(name = "accumulated_at_transfer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal accumulatedAtTransfer = BigDecimal.ZERO;

    @Column(name = "net_book_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal netBookValue = BigDecimal.ZERO;

    @Column(name = "reference")
    private String reference;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AssetTransferStatus status = AssetTransferStatus.DRAFT;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "reversal_journal_entry_id")
    private Long reversalJournalEntryId;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isDraft() {
        return status == AssetTransferStatus.DRAFT;
    }

    @Transient
    public boolean isCompleted() {
        return status == AssetTransferStatus.COMPLETED;
    }

    @Transient
    public boolean isCancelled() {
        return status == AssetTransferStatus.CANCELLED;
    }

    @Transient
    public boolean isVoided() {
        return status == AssetTransferStatus.VOID;
    }

    @Transient
    public boolean isCompletable() {
        return status == AssetTransferStatus.DRAFT;
    }

    @Transient
    public boolean isMovingLocation() {
        return !Objects.equals(fromLocationId, toLocationId)
                || !Objects.equals(fromLocation, toLocation);
    }

    @Transient
    public boolean isMovingCustodian() {
        return !Objects.equals(fromCustodian, toCustodian);
    }

    @Transient
    public boolean isMovingAccounts() {
        return !Objects.equals(fromAssetAccountId, toAssetAccountId)
                || !Objects.equals(fromAccumulatedAccountId, toAccumulatedAccountId);
    }
}
