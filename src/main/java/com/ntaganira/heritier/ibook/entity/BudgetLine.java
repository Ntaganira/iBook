/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BudgetLine.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : One budgeted account, spread across the twelve months of a budget
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "budget_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "budget")
@ToString(exclude = "budget")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    /** Denormalised so a comparison does not have to re-read the chart to know the sign. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "m1", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m1 = BigDecimal.ZERO;

    @Column(name = "m2", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m2 = BigDecimal.ZERO;

    @Column(name = "m3", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m3 = BigDecimal.ZERO;

    @Column(name = "m4", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m4 = BigDecimal.ZERO;

    @Column(name = "m5", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m5 = BigDecimal.ZERO;

    @Column(name = "m6", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m6 = BigDecimal.ZERO;

    @Column(name = "m7", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m7 = BigDecimal.ZERO;

    @Column(name = "m8", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m8 = BigDecimal.ZERO;

    @Column(name = "m9", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m9 = BigDecimal.ZERO;

    @Column(name = "m10", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m10 = BigDecimal.ZERO;

    @Column(name = "m11", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m11 = BigDecimal.ZERO;

    @Column(name = "m12", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal m12 = BigDecimal.ZERO;

    @Column(name = "annual_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal annualAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    /** One-based, and month 1 is the budget's own first month rather than January. */
    @Transient
    public BigDecimal amountForMonth(int month) {
        return switch (month) {
            case 1 -> zero(m1);
            case 2 -> zero(m2);
            case 3 -> zero(m3);
            case 4 -> zero(m4);
            case 5 -> zero(m5);
            case 6 -> zero(m6);
            case 7 -> zero(m7);
            case 8 -> zero(m8);
            case 9 -> zero(m9);
            case 10 -> zero(m10);
            case 11 -> zero(m11);
            case 12 -> zero(m12);
            default -> BigDecimal.ZERO;
        };
    }

    public void setAmountForMonth(int month, BigDecimal value) {
        BigDecimal v = zero(value);
        switch (month) {
            case 1 -> m1 = v;
            case 2 -> m2 = v;
            case 3 -> m3 = v;
            case 4 -> m4 = v;
            case 5 -> m5 = v;
            case 6 -> m6 = v;
            case 7 -> m7 = v;
            case 8 -> m8 = v;
            case 9 -> m9 = v;
            case 10 -> m10 = v;
            case 11 -> m11 = v;
            case 12 -> m12 = v;
            default -> throw new IllegalArgumentException("A budget has twelve months, not " + month);
        }
    }

    /** Inclusive on both ends, both one-based. */
    @Transient
    public BigDecimal amountForMonths(int fromMonth, int toMonth) {
        BigDecimal total = BigDecimal.ZERO;
        for (int m = Math.max(1, fromMonth); m <= Math.min(12, toMonth); m++) {
            total = total.add(amountForMonth(m));
        }
        return total;
    }

    @Transient
    public BigDecimal getMonthlyTotal() {
        return amountForMonths(1, 12);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
