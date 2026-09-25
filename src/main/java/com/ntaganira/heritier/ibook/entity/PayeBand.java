/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PayeBand.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One band of the monthly PAYE table
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One row of the monthly PAYE table: everything from {@code bandFloor} up to {@code bandCeiling}
 * is taxed at {@code rate}.
 *
 * <p>The table is <strong>data, not code</strong>. Rwandan PAYE bands have moved more than once,
 * and a rate held in a constant needs a rebuild and a deployment to change — which is how a
 * payroll quietly goes on using last year's figures. Held here they are visible on screen and can
 * be checked against the RRA schedule by somebody who knows what it says.
 */
@Entity
@Table(name = "paye_bands")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "settings")
@ToString(exclude = "settings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayeBand implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id")
    private PayrollSettings settings;

    @Column(name = "band_floor", nullable = false, precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal bandFloor = BigDecimal.ZERO;

    /** Null means the band runs on without limit, which exactly one band has to do. */
    @Column(name = "band_ceiling", precision = 16, scale = 2)
    private BigDecimal bandCeiling;

    @Column(name = "rate", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isOpenEnded() {
        return bandCeiling == null;
    }

    /** The slice of {@code taxable} that falls inside this band, which may be nothing. */
    public BigDecimal sliceOf(BigDecimal taxable) {
        BigDecimal amount = taxable == null ? BigDecimal.ZERO : taxable;
        BigDecimal from = bandFloor == null ? BigDecimal.ZERO : bandFloor;
        if (amount.compareTo(from) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal top = bandCeiling == null || amount.compareTo(bandCeiling) < 0 ? amount : bandCeiling;
        return top.subtract(from);
    }
}
