/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : FixedAsset.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a registered fixed asset
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.AssetStatus;
import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_assets")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedAsset implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_no", nullable = false, unique = true)
    private String assetNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    /** Denormalised name alongside categoryId, the way Product carries its brand. */
    @Column(name = "category")
    private String category;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location")
    private String location;

    /** Who holds it. Moved by /assets/transfers rather than edited in passing. */
    @Column(name = "custodian")
    private String custodian;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "tag_number")
    private String tagNumber;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name")
    private String vendorName;

    /** The bill or expense that bought it; the register does not post, that document did. */
    @Column(name = "purchase_reference")
    private String purchaseReference;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_cost", precision = 16, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    /** What it is expected to be worth at the end of its life; never depreciated below this. */
    @Column(name = "residual_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal residualValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false)
    @Builder.Default
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @Column(name = "useful_life_years")
    @Builder.Default
    private int usefulLifeYears = 0;

    /** Percentage per year, used by reducing balance only. */
    @Column(name = "declining_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal decliningRate = BigDecimal.ZERO;

    @Column(name = "depreciation_start", nullable = false)
    private LocalDate depreciationStart;

    /** Depreciation already charged before the asset was entered here. */
    @Column(name = "opening_accumulated", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal openingAccumulated = BigDecimal.ZERO;

    /** Charged by /assets/depreciation; the register only ever reads it. */
    @Column(name = "posted_accumulated", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal postedAccumulated = BigDecimal.ZERO;

    @Column(name = "depreciated_to")
    private LocalDate depreciatedTo;

    @Column(name = "asset_account_id")
    private Long assetAccountId;

    @Column(name = "asset_account_code")
    private String assetAccountCode;

    @Column(name = "asset_account_name")
    private String assetAccountName;

    @Column(name = "accumulated_account_id")
    private Long accumulatedAccountId;

    @Column(name = "accumulated_account_code")
    private String accumulatedAccountCode;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "expense_account_code")
    private String expenseAccountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AssetStatus status = AssetStatus.DRAFT;

    @Column(name = "disposed_date")
    private LocalDate disposedDate;

    @Column(name = "disposal_proceeds", precision = 16, scale = 2)
    private BigDecimal disposalProceeds;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

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
    public boolean isEditable() {
        return status == AssetStatus.DRAFT || status == AssetStatus.ACTIVE;
    }

    @Transient
    public boolean isInService() {
        return status == AssetStatus.ACTIVE;
    }

    @Transient
    public boolean isRetired() {
        return status == AssetStatus.DISPOSED || status == AssetStatus.WRITTEN_OFF;
    }

    @Transient
    public boolean isDepreciating() {
        return depreciationMethod != DepreciationMethod.NONE && getDepreciableAmount().signum() > 0;
    }

    /** Cost less what it will still be worth: the most that can ever be written off. */
    @Transient
    public BigDecimal getDepreciableAmount() {
        BigDecimal cost = acquisitionCost == null ? BigDecimal.ZERO : acquisitionCost;
        BigDecimal residual = residualValue == null ? BigDecimal.ZERO : residualValue;
        BigDecimal amount = cost.subtract(residual);
        return amount.signum() > 0 ? amount : BigDecimal.ZERO;
    }

    /** What the ledger has actually been charged, opening balance included. */
    @Transient
    public BigDecimal getAccumulatedDepreciation() {
        BigDecimal opening = openingAccumulated == null ? BigDecimal.ZERO : openingAccumulated;
        BigDecimal posted = postedAccumulated == null ? BigDecimal.ZERO : postedAccumulated;
        return opening.add(posted);
    }

    @Transient
    public BigDecimal getNetBookValue() {
        BigDecimal cost = acquisitionCost == null ? BigDecimal.ZERO : acquisitionCost;
        return cost.subtract(getAccumulatedDepreciation());
    }

    @Transient
    public boolean isFullyDepreciated() {
        return getDepreciableAmount().signum() > 0
                && getAccumulatedDepreciation().compareTo(getDepreciableAmount()) >= 0;
    }
}
