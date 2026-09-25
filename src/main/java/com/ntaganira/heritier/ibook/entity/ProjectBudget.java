/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ProjectBudget.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for what a job was expected to earn, cost and take
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_budgets")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectBudget implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One budget per job. Revisions are deliberately not modelled: two live budgets for the same
     * job would leave every variance on the page ambiguous about which plan it measured against.
     */
    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "project_code", nullable = false)
    private String projectCode;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "budgeted_hours", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal budgetedHours = BigDecimal.ZERO;

    @Column(name = "total_revenue", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

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
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<ProjectBudgetLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(ProjectBudgetLine line) {
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

    /**
     * Approving freezes the figures. A comparison cannot mean anything if the plan it measures
     * against is still being edited while the ledger fills up underneath it.
     */
    @Transient
    public boolean isEditable() {
        return status == BudgetStatus.DRAFT;
    }

    @Transient
    public BigDecimal getBudgetedMargin() {
        return zero(totalRevenue).subtract(zero(totalCost));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
