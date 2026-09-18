/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : DepreciationEntry.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one asset's share of a depreciation run
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "depreciation_entries")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "run")
@ToString(exclude = "run")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depreciation_run_id", nullable = false)
    private DepreciationRun run;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_no")
    private String assetNo;

    @Column(name = "asset_name")
    private String assetName;

    @Column(name = "category")
    private String category;

    @Column(name = "acquisition_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    /** What the asset had been written down by before this run charged anything. */
    @Column(name = "opening_accumulated", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal openingAccumulated = BigDecimal.ZERO;

    /** Worked out when the run was prepared; replaced by the figure actually posted. */
    @Column(name = "charge", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal charge = BigDecimal.ZERO;

    @Column(name = "closing_accumulated", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal closingAccumulated = BigDecimal.ZERO;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "expense_account_code")
    private String expenseAccountCode;

    @Column(name = "accumulated_account_id")
    private Long accumulatedAccountId;

    @Column(name = "accumulated_account_code")
    private String accumulatedAccountCode;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public BigDecimal getClosingNbv() {
        BigDecimal cost = acquisitionCost == null ? BigDecimal.ZERO : acquisitionCost;
        return cost.subtract(closingAccumulated == null ? BigDecimal.ZERO : closingAccumulated);
    }

    @Transient
    public boolean isCharged() {
        return charge != null && charge.signum() > 0;
    }
}
