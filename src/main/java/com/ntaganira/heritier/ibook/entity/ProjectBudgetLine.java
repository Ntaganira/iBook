/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ProjectBudgetLine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one planned figure on a project budget
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "project_budget_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "budget")
@ToString(exclude = "budget")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectBudgetLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private ProjectBudget budget;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    /**
     * Held on the line rather than looked up, so a budget approved against a revenue account still
     * reads as revenue if somebody later reclassifies that account in the chart.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isRevenue() {
        return accountType == AccountType.REVENUE;
    }

    @Transient
    public BigDecimal getAmountValue() {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
