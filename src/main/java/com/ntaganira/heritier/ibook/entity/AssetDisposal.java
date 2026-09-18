/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : AssetDisposal.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : JPA entity for taking a fixed asset off the register
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.DisposalMethod;
import com.ntaganira.heritier.ibook.enums.DisposalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_disposals")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDisposal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposal_no", nullable = false, unique = true)
    private String disposalNo;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_no")
    private String assetNo;

    @Column(name = "asset_name")
    private String assetName;

    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @Builder.Default
    private DisposalMethod method = DisposalMethod.SOLD;

    @Column(name = "buyer")
    private String buyer;

    @Column(name = "reference")
    private String reference;

    @Column(name = "proceeds", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal proceeds = BigDecimal.ZERO;

    /** Cost as it stood when the disposal was posted. */
    @Column(name = "cost_at_disposal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal costAtDisposal = BigDecimal.ZERO;

    /** Depreciation charged before this disposal caught the asset up. */
    @Column(name = "accumulated_before", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal accumulatedBefore = BigDecimal.ZERO;

    /** Depreciation still owed at the disposal date, charged by the disposal itself. */
    @Column(name = "catch_up_depreciation", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal catchUpDepreciation = BigDecimal.ZERO;

    @Column(name = "accumulated_at_disposal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal accumulatedAtDisposal = BigDecimal.ZERO;

    @Column(name = "net_book_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal netBookValue = BigDecimal.ZERO;

    /** Proceeds less net book value: positive is a gain, negative a loss. */
    @Column(name = "gain_or_loss", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal gainOrLoss = BigDecimal.ZERO;

    @Column(name = "proceeds_account_id")
    private Long proceedsAccountId;

    @Column(name = "proceeds_account_code")
    private String proceedsAccountCode;

    @Column(name = "gain_account_id")
    private Long gainAccountId;

    @Column(name = "gain_account_code")
    private String gainAccountCode;

    @Column(name = "loss_account_id")
    private Long lossAccountId;

    @Column(name = "loss_account_code")
    private String lossAccountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DisposalStatus status = DisposalStatus.DRAFT;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "reversal_journal_entry_id")
    private Long reversalJournalEntryId;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

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
        return status == DisposalStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return status == DisposalStatus.POSTED;
    }

    @Transient
    public boolean isVoided() {
        return status == DisposalStatus.VOID;
    }

    @Transient
    public boolean isPostable() {
        return status == DisposalStatus.DRAFT;
    }

    @Transient
    public boolean isGain() {
        return gainOrLoss != null && gainOrLoss.signum() > 0;
    }

    @Transient
    public boolean isLoss() {
        return gainOrLoss != null && gainOrLoss.signum() < 0;
    }

    @Transient
    public BigDecimal getLossAmount() {
        return isLoss() ? gainOrLoss.negate() : BigDecimal.ZERO;
    }
}
