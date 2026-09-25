/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : WithholdingRate.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : One rate in the withholding tax table
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One rate of withholding tax, with a note of what it is for.
 *
 * <p>Held as data for the same reason the PAYE bands are: a withholding rate is a matter of tax law
 * that changes, and a rate in a constant needs a rebuild to correct. Which rate applies to a given
 * payment is a judgement about the nature of the supply, so the table carries a description and the
 * rate is <strong>chosen per certificate</strong> rather than inferred from the supplier.
 */
@Entity
@Table(name = "withholding_rates")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "settings")
@ToString(exclude = "settings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithholdingRate implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id")
    private WithholdingSettings settings;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rate", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    /** What sort of payment this rate is for, in words. The judgement is the user's, not the code's. */
    @Column(name = "applies_to", length = 300)
    private String appliesTo;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    /** What this rate takes off a given taxable base. */
    public BigDecimal on(BigDecimal base) {
        BigDecimal amount = base == null ? BigDecimal.ZERO : base;
        BigDecimal percent = rate == null ? BigDecimal.ZERO : rate;
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
