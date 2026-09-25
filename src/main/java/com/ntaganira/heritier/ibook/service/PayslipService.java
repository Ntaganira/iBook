/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PayslipService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payslips across every run, for looking one up
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Employee;
import com.ntaganira.heritier.ibook.entity.PayrollRun;
import com.ntaganira.heritier.ibook.entity.Payslip;
import com.ntaganira.heritier.ibook.enums.EmployeeStatus;
import com.ntaganira.heritier.ibook.repository.EmployeeRepository;
import com.ntaganira.heritier.ibook.repository.PayslipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payslips across every run.
 *
 * <p>The list exists because somebody looking for one person's payslip does not know or care which
 * run produced it. Nothing here changes anything: a payslip is worked out by the run and frozen
 * when it posts, so this is a reading service only.
 */
@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;

    public PayslipService(PayslipRepository payslipRepository,
                          EmployeeRepository employeeRepository) {
        this.payslipRepository = payslipRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public Page<Payslip> list(String q, Long employeeId, String status,
                              LocalDate from, LocalDate to, Pageable pageable) {
        return payslipRepository.search(trimToNull(q), employeeId,
                PayrollRunService.parseStatus(status), from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Payslip get(Long id) {
        return id == null ? null : payslipRepository.findById(id).orElse(null);
    }

    /**
     * A payslip with the two things about its run that the detail page needs. Read inside the
     * transaction and flattened, rather than handing a lazy {@code run} proxy to a template.
     */
    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        Payslip payslip = id == null ? null : payslipRepository.findById(id).orElse(null);
        if (payslip == null) {
            return null;
        }
        payslip.getLines().size();
        PayrollRun run = payslip.getRun();
        return new Detail(payslip, run == null ? null : run.getId(),
                run == null ? null : run.getStatus().name(),
                run == null ? null : run.getRateNote());
    }

    @Transactional(readOnly = true)
    public List<Payslip> forEmployee(Long employeeId) {
        return employeeId == null ? List.of() : payslipRepository.findForEmployee(employeeId);
    }

    @Transactional(readOnly = true)
    public List<Employee> employees() {
        return employeeRepository.findByStatusOrderByLastNameAscFirstNameAsc(EmployeeStatus.ACTIVE);
    }

    /**
     * What the posted runs in a period add up to. Posted only — a draft run has created no
     * liability, and counting it would suggest money is due that the ledger does not yet owe.
     */
    @Transactional(readOnly = true)
    public PeriodTotals totals(LocalDate from, LocalDate to) {
        List<Payslip> slips = payslipRepository.postedBetween(from, to);
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal paye = BigDecimal.ZERO;
        BigDecimal cbhi = BigDecimal.ZERO;
        BigDecimal rssb = BigDecimal.ZERO;
        for (Payslip slip : slips) {
            gross = gross.add(zero(slip.getGrossPay()));
            net = net.add(zero(slip.getNetPay()));
            paye = paye.add(zero(slip.getPaye()));
            cbhi = cbhi.add(zero(slip.getCbhi()));
            rssb = rssb.add(zero(slip.getPensionEmployee())).add(zero(slip.getPensionEmployer()))
                    .add(zero(slip.getMaternityEmployee())).add(zero(slip.getMaternityEmployer()))
                    .add(zero(slip.getOccupationalHazard()))
                    .add(zero(slip.getMedicalEmployee())).add(zero(slip.getMedicalEmployer()));
        }
        return new PeriodTotals(slips.size(), gross, net, paye, rssb, cbhi);
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

    public record Detail(Payslip payslip, Long runId, String runStatus, String rateNote) {}

    public record PeriodTotals(int slips, BigDecimal gross, BigDecimal net,
                               BigDecimal paye, BigDecimal rssb, BigDecimal cbhi) {}
}
