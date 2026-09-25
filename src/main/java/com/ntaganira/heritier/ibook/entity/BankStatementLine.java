/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BankStatementLine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One line off a bank statement
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line off the bank statement.
 *
 * <p>Matching a statement line to a ledger line asserts that the two describe the same movement of
 * money. It writes nothing to the ledger — the match lives here, on the statement side, so that
 * reconciling leaves the books exactly as it found them.
 */
@Entity
@Table(name = "bank_statement_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "reconciliation")
@ToString(exclude = "reconciliation")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankStatementLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id")
    private BankReconciliation reconciliation;

    @Column(name = "line_date", nullable = false)
    private LocalDate lineDate;

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    private String reference;

    @Column(name = "money_in", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal moneyIn = BigDecimal.ZERO;

    @Column(name = "money_out", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal moneyOut = BigDecimal.ZERO;

    /** The ledger line this is the same movement as, once somebody has said so. */
    @Column(name = "matched_line_id")
    private Long matchedLineId;

    @Column(name = "matched_entry_no")
    private String matchedEntryNo;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isMatched() {
        return matchedLineId != null;
    }

    /** Positive is money arriving, negative is money leaving. */
    @Transient
    public BigDecimal getNet() {
        return zero(moneyIn).subtract(zero(moneyOut));
    }

    @Transient
    public BigDecimal getMoneyInValue() {
        return zero(moneyIn);
    }

    @Transient
    public BigDecimal getMoneyOutValue() {
        return zero(moneyOut);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
