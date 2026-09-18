/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ExpenseLine.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for an expense line item
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "expense_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "expense")
@ToString(exclude = "expense")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @Column(name = "description", nullable = false)
    private String description;

    /** A receipt line is a single amount rather than a quantity times a price. */
    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", nullable = false)
    @Builder.Default
    private TaxTreatment taxTreatment = TaxTreatment.STANDARD;

    @Column(name = "line_tax", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal lineTax = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "expense_account_code")
    private String expenseAccountCode;

    @Column(name = "expense_account_name")
    private String expenseAccountName;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
