/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PayrollRun.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One period of payroll for a set of people
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One period of payroll.
 *
 * <p>A draft run is a calculation nobody has committed to: payslips are worked out and can be
 * looked over, and recalculating throws them away and does it again from whatever the employee
 * records now say. Posting is what makes it real — one balanced journal entry, after which the
 * payslips are frozen, because a payslip is a statement given to a person about money that has
 * been accounted for.
 *
 * <p>Voiding writes a reversing entry rather than unpicking the original, the same treatment every
 * other posting document in this system gets.
 */
@Entity
@Table(name = "payroll_runs")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "payslips")
@ToString(exclude = "payslips")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_no", nullable = false, unique = true)
    private String runNo;

    @Column(name = "name")
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** The day the money is meant to reach people, which is the date the entry carries. */
    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

    @Column(name = "employee_count")
    @Builder.Default
    private int employeeCount = 0;

    @Column(name = "total_basic", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalBasic = BigDecimal.ZERO;

    @Column(name = "total_allowances", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    @Column(name = "total_gross", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_taxable", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxable = BigDecimal.ZERO;

    @Column(name = "total_paye", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalPaye = BigDecimal.ZERO;

    @Column(name = "total_pension_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalPensionEmployee = BigDecimal.ZERO;

    @Column(name = "total_pension_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalPensionEmployer = BigDecimal.ZERO;

    @Column(name = "total_maternity_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalMaternityEmployee = BigDecimal.ZERO;

    @Column(name = "total_maternity_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalMaternityEmployer = BigDecimal.ZERO;

    @Column(name = "total_occupational_hazard", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalOccupationalHazard = BigDecimal.ZERO;

    @Column(name = "total_medical_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalMedicalEmployee = BigDecimal.ZERO;

    @Column(name = "total_medical_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalMedicalEmployer = BigDecimal.ZERO;

    @Column(name = "total_cbhi", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalCbhi = BigDecimal.ZERO;

    @Column(name = "total_other_deductions", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalOtherDeductions = BigDecimal.ZERO;

    @Column(name = "total_net_pay", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalNetPay = BigDecimal.ZERO;

    /**
     * What the company actually spends: gross pay plus the contributions it pays on top. Kept
     * because gross pay on its own understates the cost of employing somebody by the employer
     * contributions, which is the figure a budget needs.
     */
    @Column(name = "total_employer_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalEmployerCost = BigDecimal.ZERO;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /**
     * The rates this run was worked out with, written down as text. A run posted in March must go
     * on explaining itself after the rates change in April.
     */
    @Column(name = "rate_note", length = 1000)
    private String rateNote;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("employeeName asc")
    private List<Payslip> payslips = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addPayslip(Payslip payslip) {
        payslip.setRun(this);
        payslips.add(payslip);
    }

    @Transient
    public boolean isDraft() {
        return status == PayrollRunStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return status == PayrollRunStatus.POSTED;
    }

    @Transient
    public boolean isVoided() {
        return status == PayrollRunStatus.VOID;
    }

    @Transient
    public boolean isEditable() {
        return status == PayrollRunStatus.DRAFT;
    }

    @Transient
    public BigDecimal getTotalStatutory() {
        return zero(totalPaye)
                .add(zero(totalPensionEmployee)).add(zero(totalPensionEmployer))
                .add(zero(totalMaternityEmployee)).add(zero(totalMaternityEmployer))
                .add(zero(totalOccupationalHazard))
                .add(zero(totalMedicalEmployee)).add(zero(totalMedicalEmployer))
                .add(zero(totalCbhi));
    }

    @Transient
    public BigDecimal getTotalEmployerContributions() {
        return zero(totalPensionEmployer).add(zero(totalMaternityEmployer))
                .add(zero(totalOccupationalHazard)).add(zero(totalMedicalEmployer));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
