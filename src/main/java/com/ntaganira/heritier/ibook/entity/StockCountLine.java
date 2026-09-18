/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : StockCountLine.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one product on a stock count sheet
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_count_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "stockCount")
@ToString(exclude = "stockCount")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit")
    private String unit;

    /** What the books said when the sheet was opened, kept so the drift is visible afterwards. */
    @Column(name = "expected_quantity", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal expectedQuantity = BigDecimal.ZERO;

    /** Null means nobody counted this line — not that none was found. */
    @Column(name = "counted_quantity", precision = 14, scale = 2)
    private BigDecimal countedQuantity;

    /** On hand at the moment the count was posted; the variance is measured against this. */
    @Column(name = "book_quantity", precision = 14, scale = 2)
    private BigDecimal bookQuantity;

    @Column(name = "unit_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isCounted() {
        return countedQuantity != null;
    }

    /**
     * Counted less what the books say. Before posting that is the opening snapshot; afterwards it is
     * the figure the adjustment was actually written against.
     */
    @Transient
    public BigDecimal getVariance() {
        if (countedQuantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal book = bookQuantity != null ? bookQuantity
                : (expectedQuantity == null ? BigDecimal.ZERO : expectedQuantity);
        return countedQuantity.subtract(book);
    }

    @Transient
    public BigDecimal getVarianceValue() {
        return getVariance().multiply(unitCost == null ? BigDecimal.ZERO : unitCost);
    }

    @Transient
    public boolean isOver() {
        return getVariance().signum() > 0;
    }

    @Transient
    public boolean isUnder() {
        return getVariance().signum() < 0;
    }

    /** True when stock moved between the sheet being opened and the count being posted. */
    @Transient
    public boolean isDrifted() {
        if (bookQuantity == null || expectedQuantity == null) {
            return false;
        }
        return bookQuantity.compareTo(expectedQuantity) != 0;
    }
}
