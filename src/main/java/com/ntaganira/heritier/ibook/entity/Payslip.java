/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Payslip.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One person's pay for one period
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One person's pay for one period.
 *
 * <p>Everything about the person is <strong>copied on at calculation time</strong> rather than
 * read back through the employee record — the name, the RSSB and TIN numbers, the basic salary.
 * A payslip is a statement about a particular month, and it must go on saying what it said after
 * somebody is given a raise, changes their bank or leaves.
 */
@Entity
@Table(name = "payslips")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"run", "lines"})
@ToString(exclude = {"run", "lines"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payslip implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private PayrollRun run;

    /**
     * The run's number and pay date, copied on. A payslip is looked at far more often in a list of
     * payslips than through the run that made it, and reading them back through {@code run} would
     * be one query per row plus a bet on the session still being open when the page renders.
     */
    @Column(name = "run_no")
    private String runNo;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_no")
    private String employeeNo;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @Column(name = "rssb_number")
    private String rssbNumber;

    @Column(name = "tin_number")
    private String tinNumber;

    @Column(name = "basic_salary", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "total_allowances", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    @Column(name = "gross_pay", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal grossPay = BigDecimal.ZERO;

    /** The pay contributions were worked out on, after any ceiling. */
    @Column(name = "contributory_pay", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal contributoryPay = BigDecimal.ZERO;

    @Column(name = "taxable_pay", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal taxablePay = BigDecimal.ZERO;

    @Column(name = "paye", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal paye = BigDecimal.ZERO;

    @Column(name = "pension_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal pensionEmployee = BigDecimal.ZERO;

    @Column(name = "pension_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal pensionEmployer = BigDecimal.ZERO;

    @Column(name = "maternity_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal maternityEmployee = BigDecimal.ZERO;

    @Column(name = "maternity_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal maternityEmployer = BigDecimal.ZERO;

    @Column(name = "occupational_hazard", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal occupationalHazard = BigDecimal.ZERO;

    @Column(name = "medical_employee", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal medicalEmployee = BigDecimal.ZERO;

    @Column(name = "medical_employer", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal medicalEmployer = BigDecimal.ZERO;

    @Column(name = "cbhi", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal cbhi = BigDecimal.ZERO;

    @Column(name = "other_deductions", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_pay", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "employer_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal employerCost = BigDecimal.ZERO;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<PayslipLine> lines = new ArrayList<>();

    public void addLine(PayslipLine line) {
        line.setPayslip(this);
        lines.add(line);
    }

    @Transient
    public BigDecimal getEmployerContributions() {
        return zero(pensionEmployer).add(zero(maternityEmployer))
                .add(zero(occupationalHazard)).add(zero(medicalEmployer));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
