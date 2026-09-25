/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PayrollComponent.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : A named allowance or deduction that payslips can carry
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PayrollComponentCalculation;
import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A named thing added to pay or taken off it — a transport allowance, a staff loan repayment.
 *
 * <p>Allowances and deductions are one entity because they are the same record with the sign
 * reversed, and splitting them would mean maintaining two of everything. The two pages under
 * Payroll are two filtered views of this list.
 *
 * <p>Two flags on an allowance decide real money and are deliberately <strong>not</strong>
 * defaulted from the name. Whether an allowance is taxable, and whether it counts towards pension,
 * are questions of tax and social security law that vary by allowance and change over time; a
 * system that guessed from the word "transport" would be wrong quietly. Both start off
 * <em>included</em>, which is the treatment that cannot under-declare.
 */
@Entity
@Table(name = "payroll_components")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollComponent implements Serializable {

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
    @Column(name = "kind", nullable = false)
    @Builder.Default
    private PayrollComponentKind kind = PayrollComponentKind.ALLOWANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation", nullable = false)
    @Builder.Default
    private PayrollComponentCalculation calculation = PayrollComponentCalculation.FIXED_AMOUNT;

    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "percent", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal percent = BigDecimal.ZERO;

    /** Whether this allowance goes into the pay PAYE is worked out on. Ignored for a deduction. */
    @Column(name = "taxable", nullable = false)
    @Builder.Default
    private boolean taxable = true;

    /** Whether this allowance goes into the pay contributions are worked out on. */
    @Column(name = "pensionable", nullable = false)
    @Builder.Default
    private boolean pensionable = true;

    /**
     * Where the money goes in the ledger. An allowance debits an expense account; a deduction
     * credits whatever it is recovering against, which for a staff loan is the receivable it pays
     * down and not an income account.
     */
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

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
    public boolean isAllowance() {
        return kind == PayrollComponentKind.ALLOWANCE;
    }

    @Transient
    public boolean isDeduction() {
        return kind == PayrollComponentKind.DEDUCTION;
    }

    /** What this component is worth against a given basic salary. */
    public BigDecimal amountFor(BigDecimal basic) {
        if (calculation == PayrollComponentCalculation.PERCENT_OF_BASIC) {
            BigDecimal base = basic == null ? BigDecimal.ZERO : basic;
            BigDecimal rate = percent == null ? BigDecimal.ZERO : percent;
            return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
