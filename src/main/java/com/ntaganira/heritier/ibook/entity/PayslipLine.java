/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PayslipLine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One allowance or deduction as it appeared on a payslip
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One allowance or deduction exactly as it was applied.
 *
 * <p>The taxable and pensionable flags are copied here as well as the amount. Without them a
 * payslip could not be re-checked later: the same allowance may stop being taxable, and the
 * question a query asks afterwards is what the treatment <em>was</em>, not what it is now.
 */
@Entity
@Table(name = "payslip_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "payslip")
@ToString(exclude = "payslip")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayslipLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id")
    private Payslip payslip;

    @Column(name = "component_id")
    private Long componentId;

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    @Builder.Default
    private PayrollComponentKind kind = PayrollComponentKind.ALLOWANCE;

    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "taxable", nullable = false)
    @Builder.Default
    private boolean taxable = true;

    @Column(name = "pensionable", nullable = false)
    @Builder.Default
    private boolean pensionable = true;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isAllowance() {
        return kind == PayrollComponentKind.ALLOWANCE;
    }

    @Transient
    public BigDecimal getAmountValue() {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
