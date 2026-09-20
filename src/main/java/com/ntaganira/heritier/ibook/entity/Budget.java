/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Budget.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : JPA entity for an operating budget over a fiscal year
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budgets")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    /** The month the budget's first column covers; the twelve run from here. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(name = "total_revenue", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_expense", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalExpense = BigDecimal.ZERO;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    @Builder.Default
    private List<BudgetLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(BudgetLine line) {
        line.setBudget(this);
        lines.add(line);
    }

    @Transient
    public boolean isDraft() {
        return status == BudgetStatus.DRAFT;
    }

    @Transient
    public boolean isApproved() {
        return status == BudgetStatus.APPROVED;
    }

    @Transient
    public boolean isClosed() {
        return status == BudgetStatus.CLOSED;
    }

    @Transient
    public boolean isEditable() {
        return status == BudgetStatus.DRAFT;
    }

    /** The last day the twelfth month covers. */
    @Transient
    public LocalDate getEndDate() {
        return startDate == null ? null : startDate.plusMonths(12).minusDays(1);
    }

    @Transient
    public BigDecimal getBudgetedProfit() {
        BigDecimal revenue = totalRevenue == null ? BigDecimal.ZERO : totalRevenue;
        BigDecimal expense = totalExpense == null ? BigDecimal.ZERO : totalExpense;
        return revenue.subtract(expense);
    }
}
