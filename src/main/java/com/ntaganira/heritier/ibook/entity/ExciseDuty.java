/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ExciseDuty.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : One excise duty rate and what it applies to
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ExciseBasis;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * One excise duty: a rate, what it is charged on, and which products carry it.
 *
 * <p>Excise is charged on particular goods rather than on trade in general, so it is attached to
 * <strong>products</strong> and not to a customer or a document. Two bases are supported because
 * Rwandan excise uses both: a percentage of value for some goods and a fixed amount per unit for
 * others. Offering only one would silently mis-charge the other half.
 *
 * <p>Like the PAYE bands and the withholding rates, these are data and ship
 * <strong>unconfirmed</strong>. Excise rates and the list of goods they cover change with each
 * finance law, and a rate held in a constant goes on being wrong until somebody rebuilds.
 *
 * <p>Excise is <em>not</em> VAT and does not behave like it. It forms part of the value that VAT is
 * then charged on, which is why the order the two are applied in matters and is stated where it
 * happens.
 */
@Entity
@Table(name = "excise_duties")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExciseDuty implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false)
    @Builder.Default
    private ExciseBasis basis = ExciseBasis.PERCENT_OF_VALUE;

    /** Used when the basis is a percentage of value. */
    @Column(name = "rate", precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    /** Used when the basis is a fixed amount for each unit sold. */
    @Column(name = "amount_per_unit", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amountPerUnit = BigDecimal.ZERO;

    @Column(name = "unit_label")
    private String unitLabel;

    @Column(name = "applies_to", length = 300)
    private String appliesTo;

    /** Where the duty is held until it is declared. A liability, never income. */
    @Column(name = "payable_account_id")
    private Long payableAccountId;

    @Column(name = "payable_account_code")
    private String payableAccountCode;

    @Column(name = "payable_account_name")
    private String payableAccountName;

    /**
     * Whether somebody has read this against the current schedule. Unconfirmed duties are listed
     * but never applied, so an unchecked rate cannot reach an invoice.
     */
    @Column(name = "confirmed", nullable = false)
    @Builder.Default
    private boolean confirmed = false;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "rate_source", length = 500)
    private String rateSource;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
    public boolean isPercentBased() {
        return basis == ExciseBasis.PERCENT_OF_VALUE;
    }

    @Transient
    public boolean isUsable() {
        return active && confirmed && payableAccountId != null;
    }

    /**
     * The duty on a given value and quantity. A percentage duty ignores the quantity because the
     * value already reflects it; a per-unit duty ignores the value for the same reason in reverse.
     */
    public BigDecimal on(BigDecimal value, BigDecimal quantity) {
        if (basis == ExciseBasis.AMOUNT_PER_UNIT) {
            BigDecimal units = quantity == null ? BigDecimal.ZERO : quantity;
            BigDecimal per = amountPerUnit == null ? BigDecimal.ZERO : amountPerUnit;
            return units.multiply(per).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = value == null ? BigDecimal.ZERO : value;
        BigDecimal percent = rate == null ? BigDecimal.ZERO : rate;
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
