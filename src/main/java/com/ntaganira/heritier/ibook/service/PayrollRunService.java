/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PayrollRunService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Works out a period of payroll and posts it to the ledger
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.PayrollRunForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out a period of payroll and, when somebody commits to it, posts it.
 *
 * <p><strong>The order the figures are worked out in is the whole of the accounting.</strong> It
 * is set out once here and nowhere else:
 *
 * <ol>
 *   <li>Gross pay is basic pay plus every allowance the person carries.</li>
 *   <li>Contributory pay is basic pay plus the allowances marked pensionable, capped at the
 *       ceiling if one is set. <em>One base serves every contribution.</em> Schemes can and do use
 *       different bases; rather than hard-code a different rule per scheme, an allowance that one
 *       scheme ignores is marked not pensionable, and the base is stated on the payslip.</li>
 *   <li>Pension, maternity, occupational hazards and medical are that base times their rates, and
 *       only for somebody the register says is in the scheme. Contributing for a non-member sends
 *       money to a fund that will not credit it.</li>
 *   <li>Taxable pay is basic plus the allowances marked taxable, less the employee's own pension
 *       and medical if the settings say those come off first. It floors at zero.</li>
 *   <li>PAYE is the band table applied to taxable pay, slice by slice.</li>
 *   <li>CBHI is a percentage of what is left after everything above — it is the last thing off,
 *       so it is worked out on net pay rather than on gross.</li>
 *   <li>Net pay is what remains.</li>
 * </ol>
 *
 * <p>Nothing here invents a rate. Every percentage and every band comes from
 * {@link PayrollSettingsService}, and a run <strong>cannot post</strong> while those figures are
 * unconfirmed. That gate is the point: statutory rates are the part of this system most likely to
 * be wrong and least likely to be noticed, and a payroll that quietly used a stale band would go
 * on under-deducting until the RRA asked for the difference with interest.
 *
 * <p>A draft run is a calculation nobody has committed to and can be thrown away and redone.
 * Posting writes <em>one</em> balanced entry for the whole run, grouped by account rather than one
 * entry per person, and freezes the payslips. Voiding writes a reversing entry and never touches
 * the original, the same treatment every other posting document here gets.
 */
@Service
public class PayrollRunService {

    private static final String MODULE = "payroll";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollComponentRepository payrollComponentRepository;
    private final PayrollSettingsService payrollSettingsService;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public PayrollRunService(PayrollRunRepository payrollRunRepository,
                             PayslipRepository payslipRepository,
                             EmployeeRepository employeeRepository,
                             PayrollComponentRepository payrollComponentRepository,
                             PayrollSettingsService payrollSettingsService,
                             AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             AuditService auditService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.employeeRepository = employeeRepository;
        this.payrollComponentRepository = payrollComponentRepository;
        this.payrollSettingsService = payrollSettingsService;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<PayrollRun> list(String q, String status, Pageable pageable) {
        return payrollRunRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public PayrollRun get(Long id) {
        return id == null ? null : payrollRunRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Payslip> payslipsOf(Long runId) {
        return runId == null ? List.of() : payslipRepository.findByRunIdOrderByEmployeeNameAsc(runId);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(PayrollRun run) {
        if (run == null || run.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(run.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public RunSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);
        return new RunSummary(
                payrollRunRepository.count(),
                payrollRunRepository.countByStatus(PayrollRunStatus.DRAFT),
                payrollRunRepository.countByStatus(PayrollRunStatus.POSTED),
                payrollRunRepository.countByStatus(PayrollRunStatus.VOID),
                zero(payrollRunRepository.netPaidBetween(yearStart, today)));
    }

    // ----- Creating and recalculating -----

    @Transactional
    public PayrollRun save(PayrollRunForm form, Long id, String username) {
        PayrollSettings settings = payrollSettingsService.current();

        if (form.periodEnd().isBefore(form.periodStart())) {
            throw new IllegalArgumentException("The period has to end after it starts");
        }
        if (form.payDate().isBefore(form.periodStart())) {
            throw new IllegalArgumentException("Pay cannot be dated before the period it is for");
        }

        List<PayrollRun> clashes = payrollRunRepository
                .overlapping(form.periodStart(), form.periodEnd(), id);
        if (!clashes.isEmpty() && form.coversEverybody()) {
            PayrollRun clash = clashes.get(0);
            throw new IllegalStateException("These weeks are already covered by " + clash.getRunNo()
                    + ". Running them again would pay everybody twice. Name the few people this "
                    + "run is for if it is an off-cycle payment.");
        }

        PayrollRun run;
        if (id == null) {
            run = new PayrollRun();
            run.setRunNo(nextRunNo());
            run.setCreatedBy(username);
        } else {
            run = payrollRunRepository.findById(id).orElseThrow();
            if (!run.isEditable()) {
                throw new IllegalStateException("Only a draft run can be changed");
            }
            run.getPayslips().clear();
        }

        run.setName(trimToNull(form.name()));
        run.setPeriodStart(form.periodStart());
        run.setPeriodEnd(form.periodEnd());
        run.setPayDate(form.payDate());
        run.setNotes(trimToNull(form.notes()));
        run.setStatus(PayrollRunStatus.DRAFT);

        List<Employee> people = peopleFor(form);
        if (people.isEmpty()) {
            throw new IllegalStateException("Nobody is payable for these weeks — check the register "
                    + "for joining and leaving dates, and that people are not all suspended");
        }

        calculateInto(run, people, settings);
        run.setRateNote(rateNote(settings));

        PayrollRun saved = payrollRunRepository.save(run);
        auditService.log(MODULE, id == null ? "CREATE_PAYROLL_RUN" : "RECALCULATE_PAYROLL_RUN",
                "payrollRun#" + saved.getId(), saved.getRunNo() + " — " + saved.getEmployeeCount()
                        + " people, net " + zero(saved.getTotalNetPay()).toPlainString());
        return saved;
    }

    private List<Employee> peopleFor(PayrollRunForm form) {
        List<Employee> candidates = form.coversEverybody()
                ? employeeRepository.findRunnable()
                : employeeRepository.findAllById(form.employeeIdsValue());
        List<Employee> payable = new ArrayList<>();
        for (Employee e : candidates) {
            if (e.isPayableIn(form.periodStart(), form.periodEnd())) {
                payable.add(e);
            }
        }
        String department = trimToNull(form.department());
        if (department != null) {
            payable.removeIf(e -> !department.equalsIgnoreCase(e.getDepartment()));
        }
        return payable;
    }

    /**
     * Works every payslip out and rolls the run totals up from them, so the totals are always the
     * sum of what is on the slips rather than a second calculation that could disagree with them.
     */
    private void calculateInto(PayrollRun run, List<Employee> people, PayrollSettings settings) {
        BigDecimal basic = BigDecimal.ZERO;
        BigDecimal allowances = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal paye = BigDecimal.ZERO;
        BigDecimal pensionEmp = BigDecimal.ZERO;
        BigDecimal pensionEr = BigDecimal.ZERO;
        BigDecimal matEmp = BigDecimal.ZERO;
        BigDecimal matEr = BigDecimal.ZERO;
        BigDecimal occ = BigDecimal.ZERO;
        BigDecimal medEmp = BigDecimal.ZERO;
        BigDecimal medEr = BigDecimal.ZERO;
        BigDecimal cbhi = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal employerCost = BigDecimal.ZERO;

        for (Employee employee : people) {
            Payslip slip = calculate(employee, settings);
            slip.setRunNo(run.getRunNo());
            slip.setPayDate(run.getPayDate());
            slip.setPeriodStart(run.getPeriodStart());
            slip.setPeriodEnd(run.getPeriodEnd());
            run.addPayslip(slip);
            basic = basic.add(slip.getBasicSalary());
            allowances = allowances.add(slip.getTotalAllowances());
            gross = gross.add(slip.getGrossPay());
            taxable = taxable.add(slip.getTaxablePay());
            paye = paye.add(slip.getPaye());
            pensionEmp = pensionEmp.add(slip.getPensionEmployee());
            pensionEr = pensionEr.add(slip.getPensionEmployer());
            matEmp = matEmp.add(slip.getMaternityEmployee());
            matEr = matEr.add(slip.getMaternityEmployer());
            occ = occ.add(slip.getOccupationalHazard());
            medEmp = medEmp.add(slip.getMedicalEmployee());
            medEr = medEr.add(slip.getMedicalEmployer());
            cbhi = cbhi.add(slip.getCbhi());
            other = other.add(slip.getOtherDeductions());
            net = net.add(slip.getNetPay());
            employerCost = employerCost.add(slip.getEmployerCost());
        }

        run.setEmployeeCount(people.size());
        run.setTotalBasic(basic);
        run.setTotalAllowances(allowances);
        run.setTotalGross(gross);
        run.setTotalTaxable(taxable);
        run.setTotalPaye(paye);
        run.setTotalPensionEmployee(pensionEmp);
        run.setTotalPensionEmployer(pensionEr);
        run.setTotalMaternityEmployee(matEmp);
        run.setTotalMaternityEmployer(matEr);
        run.setTotalOccupationalHazard(occ);
        run.setTotalMedicalEmployee(medEmp);
        run.setTotalMedicalEmployer(medEr);
        run.setTotalCbhi(cbhi);
        run.setTotalOtherDeductions(other);
        run.setTotalNetPay(net);
        run.setTotalEmployerCost(employerCost);
    }

    /** One person's pay, worked out in the order set out on this class. */
    private Payslip calculate(Employee employee, PayrollSettings settings) {
        BigDecimal basic = round(zero(employee.getBasicSalary()));

        Payslip slip = Payslip.builder()
                .employeeId(employee.getId())
                .employeeNo(employee.getEmployeeNo())
                .employeeName(employee.getFullName())
                .jobTitle(employee.getJobTitle())
                .department(employee.getDepartment())
                .rssbNumber(employee.getRssbNumber())
                .tinNumber(employee.getTinNumber())
                .basicSalary(basic)
                .build();

        BigDecimal totalAllowances = BigDecimal.ZERO;
        BigDecimal taxableAllowances = BigDecimal.ZERO;
        BigDecimal pensionableAllowances = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        int order = 0;

        for (EmployeeComponent assigned : employee.getComponents()) {
            PayrollComponent component = payrollComponentRepository
                    .findById(assigned.getComponentId()).orElse(null);
            if (component == null || !component.isActive()) {
                continue;
            }
            BigDecimal amount = round(assigned.getOverrideAmount() != null
                    ? assigned.getOverrideAmount()
                    : component.amountFor(basic));
            if (amount.signum() == 0) {
                continue;
            }
            slip.addLine(PayslipLine.builder()
                    .componentId(component.getId())
                    .code(component.getCode())
                    .name(component.getName())
                    .kind(component.getKind())
                    .amount(amount)
                    .taxable(component.isTaxable())
                    .pensionable(component.isPensionable())
                    .accountId(component.getAccountId())
                    .accountCode(component.getAccountCode())
                    .accountName(component.getAccountName())
                    .sortOrder(order++)
                    .build());
            if (component.getKind() == PayrollComponentKind.ALLOWANCE) {
                totalAllowances = totalAllowances.add(amount);
                if (component.isTaxable()) {
                    taxableAllowances = taxableAllowances.add(amount);
                }
                if (component.isPensionable()) {
                    pensionableAllowances = pensionableAllowances.add(amount);
                }
            } else {
                deductions = deductions.add(amount);
            }
        }

        BigDecimal gross = basic.add(totalAllowances);

        BigDecimal contributory = basic.add(pensionableAllowances);
        if (settings.getContributionCeiling() != null
                && contributory.compareTo(settings.getContributionCeiling()) > 0) {
            contributory = settings.getContributionCeiling();
        }

        BigDecimal pensionEmp = employee.isPensionMember()
                ? percent(contributory, settings.getPensionEmployeeRate()) : BigDecimal.ZERO;
        BigDecimal pensionEr = employee.isPensionMember()
                ? percent(contributory, settings.getPensionEmployerRate()) : BigDecimal.ZERO;
        BigDecimal occ = employee.isPensionMember()
                ? percent(contributory, settings.getOccupationalHazardRate()) : BigDecimal.ZERO;
        BigDecimal matEmp = employee.isMaternityMember()
                ? percent(contributory, settings.getMaternityEmployeeRate()) : BigDecimal.ZERO;
        BigDecimal matEr = employee.isMaternityMember()
                ? percent(contributory, settings.getMaternityEmployerRate()) : BigDecimal.ZERO;
        BigDecimal medEmp = employee.isMedicalMember()
                ? percent(contributory, settings.getMedicalEmployeeRate()) : BigDecimal.ZERO;
        BigDecimal medEr = employee.isMedicalMember()
                ? percent(contributory, settings.getMedicalEmployerRate()) : BigDecimal.ZERO;

        BigDecimal taxableGross = basic.add(taxableAllowances);
        BigDecimal reliefs = settings.isPensionDeductibleForPaye()
                ? pensionEmp.add(medEmp) : BigDecimal.ZERO;
        BigDecimal taxablePay = taxableGross.subtract(reliefs);
        if (taxablePay.signum() < 0) {
            taxablePay = BigDecimal.ZERO;
        }

        BigDecimal paye = payeOn(taxablePay, settings.getBands());

        BigDecimal netBeforeCbhi = gross.subtract(paye).subtract(pensionEmp)
                .subtract(matEmp).subtract(medEmp).subtract(deductions);
        BigDecimal cbhi = employee.isCbhiMember() && netBeforeCbhi.signum() > 0
                ? percent(netBeforeCbhi, settings.getCbhiEmployeeRate()) : BigDecimal.ZERO;
        BigDecimal net = netBeforeCbhi.subtract(cbhi);

        slip.setTotalAllowances(totalAllowances);
        slip.setGrossPay(gross);
        slip.setContributoryPay(contributory);
        slip.setTaxablePay(taxablePay);
        slip.setPaye(paye);
        slip.setPensionEmployee(pensionEmp);
        slip.setPensionEmployer(pensionEr);
        slip.setMaternityEmployee(matEmp);
        slip.setMaternityEmployer(matEr);
        slip.setOccupationalHazard(occ);
        slip.setMedicalEmployee(medEmp);
        slip.setMedicalEmployer(medEr);
        slip.setCbhi(cbhi);
        slip.setOtherDeductions(deductions);
        slip.setTotalDeductions(paye.add(pensionEmp).add(matEmp).add(medEmp).add(cbhi).add(deductions));
        slip.setNetPay(net);
        slip.setEmployerCost(gross.add(pensionEr).add(matEr).add(occ).add(medEr));
        return slip;
    }

    /**
     * PAYE slice by slice. Each band taxes only the part of pay that falls inside it, which is why
     * a raise into a higher band never leaves somebody worse off than before it.
     */
    private static BigDecimal payeOn(BigDecimal taxablePay, List<PayeBand> bands) {
        BigDecimal tax = BigDecimal.ZERO;
        for (PayeBand band : bands) {
            BigDecimal slice = band.sliceOf(taxablePay);
            if (slice.signum() > 0) {
                tax = tax.add(percent(slice, band.getRate()));
            }
        }
        return tax;
    }

    // ----- Posting -----

    /**
     * Writes the run to the ledger as one balanced entry.
     *
     * <p>Debits are what employing people cost: basic pay and each allowance to its own account,
     * plus the contributions the company pays on top. Credits are who the money is owed to: the
     * RRA, RSSB, the CBHI fund, whatever each deduction recovers against, and the people
     * themselves. Net pay is credited to a <em>payable</em>, not to the bank — posting a payroll
     * recognises a wage bill, and the bank is only touched when the money actually leaves, which
     * is a separate payment.
     */
    @Transactional
    public PayrollRun post(Long id, String username) {
        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        if (run.isPosted()) {
            return run;
        }
        if (run.isVoided()) {
            throw new IllegalStateException("A void run cannot be posted");
        }

        PayrollSettings settings = payrollSettingsService.current();
        if (!settings.isConfirmed()) {
            throw new IllegalStateException("The PAYE bands and contribution rates have not been "
                    + "confirmed. Check them against the current RRA and RSSB schedules at Payroll "
                    + "settings and confirm them there — nothing posts until somebody has.");
        }
        if (!settings.isAccountsComplete()) {
            throw new IllegalStateException("Payroll settings is missing at least one of the "
                    + "accounts a run posts to");
        }

        List<Payslip> slips = payslipRepository.findByRunIdOrderByEmployeeNameAsc(run.getId());
        if (slips.isEmpty()) {
            throw new IllegalStateException("A run with no payslips cannot be posted");
        }
        for (Payslip slip : slips) {
            if (slip.getNetPay().signum() < 0) {
                throw new IllegalStateException(slip.getEmployeeName() + " comes out at "
                        + slip.getNetPay().toPlainString() + " — deductions exceed their pay. Fix "
                        + "the deductions on their record and recalculate before posting.");
            }
        }

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(run.getPayDate())
                // ADJUSTMENT rather than a PAYROLL type of its own: Hibernate wrote a check
                // constraint on this column when the table was created and ddl-auto never widens
                // it, so a new value fails on every existing database. The run number in the
                // reference is what identifies these.
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(run.getRunNo())
                .memo("Payroll " + run.getRunNo() + " — " + run.getPeriodStart() + " to "
                        + run.getPeriodEnd())
                .createdBy(username)
                .build();

        int order = 0;
        BigDecimal debits = BigDecimal.ZERO;

        Account wages = account(settings.getWagesExpenseAccountId(), "the wages expense account");
        BigDecimal basicTotal = zero(run.getTotalBasic());
        if (basicTotal.signum() != 0) {
            entry.addLine(line(wages, "Basic pay — " + run.getRunNo(), basicTotal, BigDecimal.ZERO, order++));
            debits = debits.add(basicTotal);
        }

        Map<Long, BigDecimal> allowanceByAccount = new LinkedHashMap<>();
        Map<Long, BigDecimal> deductionByAccount = new LinkedHashMap<>();
        for (Payslip slip : slips) {
            for (PayslipLine line : slip.getLines()) {
                Long accountId = line.getAccountId() == null
                        ? (line.isAllowance() ? wages.getId() : settings.getNetPayPayableAccountId())
                        : line.getAccountId();
                Map<Long, BigDecimal> target = line.isAllowance() ? allowanceByAccount : deductionByAccount;
                target.merge(accountId, line.getAmountValue(), BigDecimal::add);
            }
        }

        for (Map.Entry<Long, BigDecimal> allowance : allowanceByAccount.entrySet()) {
            if (allowance.getValue().signum() == 0) {
                continue;
            }
            Account acc = account(allowance.getKey(), "an allowance account");
            entry.addLine(line(acc, "Allowances — " + run.getRunNo(),
                    allowance.getValue(), BigDecimal.ZERO, order++));
            debits = debits.add(allowance.getValue());
        }

        BigDecimal employerContributions = run.getTotalEmployerContributions();
        if (employerContributions.signum() != 0) {
            Account employerExpense = account(settings.getEmployerContributionAccountId(),
                    "the employer contributions account");
            entry.addLine(line(employerExpense, "Employer contributions — " + run.getRunNo(),
                    employerContributions, BigDecimal.ZERO, order++));
            debits = debits.add(employerContributions);
        }

        BigDecimal credits = BigDecimal.ZERO;

        BigDecimal paye = zero(run.getTotalPaye());
        if (paye.signum() != 0) {
            entry.addLine(line(account(settings.getPayePayableAccountId(), "the PAYE payable account"),
                    "PAYE — " + run.getRunNo(), BigDecimal.ZERO, paye, order++));
            credits = credits.add(paye);
        }

        BigDecimal rssb = zero(run.getTotalPensionEmployee()).add(zero(run.getTotalPensionEmployer()))
                .add(zero(run.getTotalMaternityEmployee())).add(zero(run.getTotalMaternityEmployer()))
                .add(zero(run.getTotalOccupationalHazard()))
                .add(zero(run.getTotalMedicalEmployee())).add(zero(run.getTotalMedicalEmployer()));
        if (rssb.signum() != 0) {
            entry.addLine(line(account(settings.getRssbPayableAccountId(), "the RSSB payable account"),
                    "RSSB contributions — " + run.getRunNo(), BigDecimal.ZERO, rssb, order++));
            credits = credits.add(rssb);
        }

        BigDecimal cbhi = zero(run.getTotalCbhi());
        if (cbhi.signum() != 0) {
            entry.addLine(line(account(settings.getCbhiPayableAccountId(), "the CBHI payable account"),
                    "CBHI — " + run.getRunNo(), BigDecimal.ZERO, cbhi, order++));
            credits = credits.add(cbhi);
        }

        for (Map.Entry<Long, BigDecimal> deduction : deductionByAccount.entrySet()) {
            if (deduction.getValue().signum() == 0) {
                continue;
            }
            Account acc = account(deduction.getKey(), "a deduction account");
            entry.addLine(line(acc, "Deductions — " + run.getRunNo(),
                    BigDecimal.ZERO, deduction.getValue(), order++));
            credits = credits.add(deduction.getValue());
        }

        BigDecimal net = zero(run.getTotalNetPay());
        if (net.signum() != 0) {
            entry.addLine(line(account(settings.getNetPayPayableAccountId(), "the net pay payable account"),
                    "Net pay — " + run.getRunNo(), BigDecimal.ZERO, net, order));
            credits = credits.add(net);
        }

        // Rounding drift lands on the last debit line so the entry balances to the franc.
        BigDecimal drift = credits.subtract(debits);
        if (drift.signum() != 0) {
            for (int i = entry.getLines().size() - 1; i >= 0; i--) {
                JournalLine candidate = entry.getLines().get(i);
                if (candidate.getDebitValue().signum() > 0) {
                    candidate.setDebit(candidate.getDebitValue().add(drift));
                    break;
                }
            }
        }

        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        if (entry.getTotalDebits().compareTo(entry.getTotalCredits()) != 0) {
            throw new IllegalStateException("This run does not balance — debits "
                    + entry.getTotalDebits().toPlainString() + " against credits "
                    + entry.getTotalCredits().toPlainString());
        }
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        run.setJournalEntryId(savedEntry.getId());
        run.setStatus(PayrollRunStatus.POSTED);
        PayrollRun saved = payrollRunRepository.save(run);
        auditService.log(MODULE, "POST_PAYROLL_RUN", "payrollRun#" + saved.getId(),
                saved.getRunNo() + " posted as " + savedEntry.getEntryNo() + " — net "
                        + net.toPlainString());
        return saved;
    }

    @Transactional
    public PayrollRun voidRun(Long id, String reason, String username) {
        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        if (run.isVoided()) {
            return run;
        }
        if (run.isPosted()) {
            JournalEntry original = journalEntryRepository.findById(run.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(run.getRunNo())
                        .memo("Reversal of " + original.getEntryNo() + " — voided payroll "
                                + run.getRunNo())
                        .createdBy(username)
                        .build();
                int order = 0;
                for (JournalLine line : original.getLines()) {
                    reversal.addLine(JournalLine.builder()
                            .accountId(line.getAccountId())
                            .accountCode(line.getAccountCode())
                            .accountName(line.getAccountName())
                            .memo(line.getMemo())
                            .debit(line.getCreditValue())
                            .credit(line.getDebitValue())
                            .sortOrder(order++)
                            .build());
                }
                reversal.setTotalDebits(sumDebits(reversal));
                reversal.setTotalCredits(sumCredits(reversal));
                journalEntryRepository.save(reversal);
            }
        }
        run.setStatus(PayrollRunStatus.VOID);
        PayrollRun saved = payrollRunRepository.save(run);
        auditService.log(MODULE, "VOID_PAYROLL_RUN", "payrollRun#" + id,
                saved.getRunNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        PayrollRun run = payrollRunRepository.findById(id).orElse(null);
        if (run == null) {
            return;
        }
        if (!run.isEditable()) {
            throw new IllegalStateException("Only a draft run can be deleted. Void it instead — a "
                    + "posted run is in the ledger and has to be reversed rather than removed.");
        }
        payrollRunRepository.delete(run);
        auditService.log(MODULE, "DELETE_PAYROLL_RUN", "payrollRun#" + id, run.getRunNo() + " draft deleted");
    }

    // ----- Helpers -----

    private Account account(Long id, String what) {
        if (id == null) {
            throw new IllegalStateException("Payroll settings has not been given " + what);
        }
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(what + " no longer exists — check "
                        + "Payroll settings against the chart of accounts"));
    }

    private static JournalLine line(Account account, String memo, BigDecimal debit,
                                    BigDecimal credit, int order) {
        return JournalLine.builder()
                .accountId(account.getId())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .memo(memo)
                .debit(debit)
                .credit(credit)
                .sortOrder(order)
                .build();
    }

    /** The rates used, written out, so a run posted before a rate change still explains itself. */
    private static String rateNote(PayrollSettings s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pension ").append(plain(s.getPensionEmployeeRate())).append("/")
                .append(plain(s.getPensionEmployerRate()))
                .append("%, hazards ").append(plain(s.getOccupationalHazardRate()))
                .append("%, maternity ").append(plain(s.getMaternityEmployeeRate())).append("/")
                .append(plain(s.getMaternityEmployerRate()))
                .append("%, medical ").append(plain(s.getMedicalEmployeeRate())).append("/")
                .append(plain(s.getMedicalEmployerRate()))
                .append("%, CBHI ").append(plain(s.getCbhiEmployeeRate())).append("%. PAYE ");
        for (PayeBand band : s.getBands()) {
            sb.append(plain(band.getBandFloor())).append("-")
                    .append(band.getBandCeiling() == null ? "up" : plain(band.getBandCeiling()))
                    .append("@").append(plain(band.getRate())).append("% ");
        }
        if (s.getRateSource() != null) {
            sb.append("| checked against ").append(s.getRateSource());
        }
        return sb.toString().trim();
    }

    private static String plain(BigDecimal value) {
        return zero(value).stripTrailingZeros().toPlainString();
    }

    private String nextRunNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("PAYROLL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "PAY-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "PAY-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private String nextJournalNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("JOURNAL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "JE-" : seq.getPrefix();
            int padding = seq.getPadding() == 0 ? 5 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next);
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "JE-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal value) {
        return zero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumDebits(JournalEntry entry) {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : entry.getLines()) {
            total = total.add(line.getDebitValue());
        }
        return total;
    }

    private static BigDecimal sumCredits(JournalEntry entry) {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : entry.getLines()) {
            total = total.add(line.getCreditValue());
        }
        return total;
    }

    public static PayrollRunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PayrollRunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record RunSummary(long all, long draft, long posted, long voided, BigDecimal netThisYear) {}
}
