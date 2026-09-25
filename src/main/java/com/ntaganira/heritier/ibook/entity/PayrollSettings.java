/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PayrollSettings.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The rates and accounts every payroll run is worked out from
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Every rate a payslip is worked out from, held as one editable record rather than as constants.
 *
 * <p>Statutory rates are the part of an accounting system most certain to change and least safe to
 * guess. These figures ship <strong>unconfirmed</strong>: they are a starting point, not an
 * authority, and a run <em>refuses to post</em> until somebody has read them against the current
 * RRA and RSSB schedules and pressed confirm. A payroll that quietly computes PAYE from figures
 * nobody checked is worse than one that will not run, because the first only comes to light when
 * the RRA asks for the difference.
 *
 * <p>Confirming records who did it and when. Editing any rate afterwards clears the confirmation,
 * so a changed figure has to be looked at again rather than inheriting somebody else's approval.
 */
@Entity
@Table(name = "payroll_settings")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "bands")
@ToString(exclude = "bands")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmed", nullable = false)
    @Builder.Default
    private boolean confirmed = false;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** What the confirmer says they checked these against. A citation, not a calculation. */
    @Column(name = "rate_source", length = 500)
    private String rateSource;

    // ----- Contribution rates, every one a percentage -----

    @Column(name = "pension_employee_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal pensionEmployeeRate = BigDecimal.ZERO;

    @Column(name = "pension_employer_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal pensionEmployerRate = BigDecimal.ZERO;

    @Column(name = "occupational_hazard_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal occupationalHazardRate = BigDecimal.ZERO;

    @Column(name = "maternity_employee_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal maternityEmployeeRate = BigDecimal.ZERO;

    @Column(name = "maternity_employer_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal maternityEmployerRate = BigDecimal.ZERO;

    @Column(name = "medical_employee_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal medicalEmployeeRate = BigDecimal.ZERO;

    @Column(name = "medical_employer_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal medicalEmployerRate = BigDecimal.ZERO;

    @Column(name = "cbhi_employee_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal cbhiEmployeeRate = BigDecimal.ZERO;

    /**
     * An upper limit on the pay contributions are worked out from. Left empty there is no limit.
     */
    @Column(name = "contribution_ceiling", precision = 16, scale = 2)
    private BigDecimal contributionCeiling;

    /**
     * Whether an employee's own pension and medical contributions come off pay before PAYE is
     * worked out. A setting rather than an assumption: it changes every payslip in the company and
     * the answer is a matter of tax law, not of arithmetic.
     */
    @Column(name = "pension_deductible_for_paye", nullable = false)
    @Builder.Default
    private boolean pensionDeductibleForPaye = true;

    // ----- The accounts a run posts to -----

    @Column(name = "wages_expense_account_id")
    private Long wagesExpenseAccountId;

    @Column(name = "employer_contribution_account_id")
    private Long employerContributionAccountId;

    @Column(name = "paye_payable_account_id")
    private Long payePayableAccountId;

    @Column(name = "rssb_payable_account_id")
    private Long rssbPayableAccountId;

    @Column(name = "cbhi_payable_account_id")
    private Long cbhiPayableAccountId;

    @Column(name = "net_pay_payable_account_id")
    private Long netPayPayableAccountId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<PayeBand> bands = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addBand(PayeBand band) {
        band.setSettings(this);
        bands.add(band);
    }

    /** A run cannot invent an account halfway through posting, so all six are required up front. */
    @Transient
    public boolean isAccountsComplete() {
        return wagesExpenseAccountId != null && employerContributionAccountId != null
                && payePayableAccountId != null && rssbPayableAccountId != null
                && cbhiPayableAccountId != null && netPayPayableAccountId != null;
    }

    @Transient
    public boolean isReadyToRun() {
        return confirmed && isAccountsComplete() && !bands.isEmpty();
    }

    @Transient
    public BigDecimal getPensionTotalRate() {
        return zero(pensionEmployeeRate).add(zero(pensionEmployerRate));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
