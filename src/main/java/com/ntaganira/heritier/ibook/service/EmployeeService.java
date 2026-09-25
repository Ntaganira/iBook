/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : EmployeeService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The register of people on the payroll
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.EmployeeForm;
import com.ntaganira.heritier.ibook.entity.Employee;
import com.ntaganira.heritier.ibook.entity.EmployeeComponent;
import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import com.ntaganira.heritier.ibook.entity.PayrollComponent;
import com.ntaganira.heritier.ibook.enums.EmployeeStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.repository.EmployeeRepository;
import com.ntaganira.heritier.ibook.repository.NumberingSequenceRepository;
import com.ntaganira.heritier.ibook.repository.PayrollComponentRepository;
import com.ntaganira.heritier.ibook.repository.PayslipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The register of people on the payroll.
 *
 * <p>Nothing here posts. An employee record is a standing instruction about how somebody is paid,
 * not a transaction — a raise changes what the next run computes and touches no figure already in
 * the ledger, which is why payslips copy everything they need rather than reading it back.
 *
 * <p>Somebody who leaves is <strong>terminated, never deleted</strong>, once they have a payslip:
 * payslips already issued name a person, and a register that lost them would leave last year's
 * payroll unable to say who it paid.
 */
@Service
public class EmployeeService {

    private static final String MODULE = "payroll";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmployeeRepository employeeRepository;
    private final PayrollComponentRepository payrollComponentRepository;
    private final PayslipRepository payslipRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           PayrollComponentRepository payrollComponentRepository,
                           PayslipRepository payslipRepository,
                           NumberingSequenceRepository numberingSequenceRepository,
                           AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.payrollComponentRepository = payrollComponentRepository;
        this.payslipRepository = payslipRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Employee> list(String q, String status, String department, Pageable pageable) {
        return employeeRepository.search(trimToNull(q), parseStatus(status),
                trimToNull(department), pageable);
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return id == null ? null : employeeRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Employee> runnable() {
        return employeeRepository.findRunnable();
    }

    @Transactional(readOnly = true)
    public List<String> departments() {
        return employeeRepository.findDepartments();
    }

    @Transactional(readOnly = true)
    public List<PayrollComponent> components() {
        return payrollComponentRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public EmployeeSummary summary() {
        return new EmployeeSummary(
                employeeRepository.count(),
                employeeRepository.countByStatus(EmployeeStatus.ACTIVE),
                employeeRepository.countByStatus(EmployeeStatus.ON_LEAVE),
                employeeRepository.countByStatus(EmployeeStatus.SUSPENDED),
                employeeRepository.countByStatus(EmployeeStatus.TERMINATED),
                zero(employeeRepository.activeBasicTotal()),
                employeeRepository.countEndingBy(LocalDate.now().plusDays(30)));
    }

    @Transactional(readOnly = true)
    public long payslipCount(Long employeeId) {
        return employeeId == null ? 0 : payslipRepository.countByEmployeeId(employeeId);
    }

    /**
     * What this person is set up to be paid before any statutory deduction. Shown on the register
     * so a basic salary that looks low is visibly a basic salary and not the whole package.
     */
    @Transactional(readOnly = true)
    public BigDecimal grossFor(Employee employee) {
        if (employee == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal basic = zero(employee.getBasicSalary());
        BigDecimal total = basic;
        for (EmployeeComponent assigned : employee.getComponents()) {
            PayrollComponent component = payrollComponentRepository
                    .findById(assigned.getComponentId()).orElse(null);
            if (component == null || !component.isActive() || !component.isAllowance()) {
                continue;
            }
            total = total.add(assigned.getOverrideAmount() != null
                    ? assigned.getOverrideAmount()
                    : component.amountFor(basic));
        }
        return total;
    }

    // ----- Editing -----

    @Transactional
    public Employee save(EmployeeForm form, Long id) {
        Employee employee;
        if (id == null) {
            employee = new Employee();
            employee.setEmployeeNo(nextEmployeeNo());
        } else {
            employee = employeeRepository.findById(id).orElseThrow();
            employee.getComponents().clear();
        }

        if (form.hireDate() != null && form.endDate() != null
                && form.endDate().isBefore(form.hireDate())) {
            throw new IllegalArgumentException("A leaving date cannot come before the joining date");
        }

        employee.setFirstName(trimToNull(form.firstName()));
        employee.setLastName(trimToNull(form.lastName()));
        employee.setNationalId(trimToNull(form.nationalId()));
        employee.setRssbNumber(trimToNull(form.rssbNumber()));
        employee.setTinNumber(trimToNull(form.tinNumber()));
        employee.setEmail(trimToNull(form.email()));
        employee.setPhone(trimToNull(form.phone()));
        employee.setJobTitle(trimToNull(form.jobTitle()));
        employee.setDepartment(trimToNull(form.department()));
        employee.setHireDate(form.hireDate());
        employee.setEndDate(form.endDate());
        employee.setBasicSalary(form.basicSalaryValue());
        employee.setCurrencyCode(form.currencyCode() == null || form.currencyCode().isBlank()
                ? "RWF" : form.currencyCode());
        employee.setPaymentMethod(parseMethod(form.paymentMethod()));
        employee.setBankName(trimToNull(form.bankName()));
        employee.setBankAccount(trimToNull(form.bankAccount()));
        employee.setMobileMoney(trimToNull(form.mobileMoney()));
        employee.setPensionMember(form.pensionMemberValue());
        employee.setMaternityMember(form.maternityMemberValue());
        employee.setMedicalMember(form.medicalMemberValue());
        employee.setCbhiMember(form.cbhiMemberValue());
        employee.setStatus(parseStatus(form.status()) == null
                ? EmployeeStatus.ACTIVE : parseStatus(form.status()));
        employee.setNotes(trimToNull(form.notes()));

        int order = 0;
        for (Long componentId : form.componentIdsValue()) {
            PayrollComponent component = payrollComponentRepository.findById(componentId).orElse(null);
            if (component == null) {
                continue;
            }
            BigDecimal override = form.overrideFor(componentId);
            // An override of zero is a real instruction — somebody suspending an allowance without
            // taking it off the record — so only a blank field falls back to the component figure.
            employee.addComponent(EmployeeComponent.builder()
                    .componentId(component.getId())
                    .componentCode(component.getCode())
                    .componentName(component.getName())
                    .kind(component.getKind())
                    .overrideAmount(override)
                    .sortOrder(order++)
                    .build());
        }

        Employee saved = employeeRepository.save(employee);
        auditService.log(MODULE, id == null ? "CREATE_EMPLOYEE" : "UPDATE_EMPLOYEE",
                "employee#" + saved.getId(), saved.getEmployeeNo() + " — " + saved.getFullName());
        return saved;
    }

    @Transactional
    public Employee setStatus(Long id, String status) {
        Employee employee = employeeRepository.findById(id).orElseThrow();
        EmployeeStatus target = parseStatus(status);
        if (target == null) {
            throw new IllegalArgumentException("That is not a status anybody can be put into");
        }
        employee.setStatus(target);
        if (target == EmployeeStatus.TERMINATED && employee.getEndDate() == null) {
            employee.setEndDate(LocalDate.now());
        }
        Employee saved = employeeRepository.save(employee);
        auditService.log(MODULE, "SET_EMPLOYEE_STATUS", "employee#" + id,
                saved.getFullName() + " — " + target.name());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = employeeRepository.findById(id).orElse(null);
        if (employee == null) {
            return;
        }
        long slips = payslipRepository.countByEmployeeId(id);
        if (slips > 0) {
            throw new IllegalStateException("This person has " + slips
                    + (slips == 1 ? " payslip" : " payslips")
                    + ". Mark them as having left instead — deleting them would leave payroll that "
                    + "has already been posted unable to say who it paid.");
        }
        employeeRepository.delete(employee);
        auditService.log(MODULE, "DELETE_EMPLOYEE", "employee#" + id,
                employee.getEmployeeNo() + " — " + employee.getFullName());
    }

    public EmployeeForm toForm(Employee e) {
        List<Long> assigned = e.getComponents().stream().map(EmployeeComponent::getComponentId).toList();
        List<Long> all = components().stream().map(PayrollComponent::getId).toList();
        // The component is found first and the override read off it afterwards. Mapping to the
        // override before findFirst() feeds a null into Optional.of, which throws.
        List<BigDecimal> overrides = all.stream()
                .map(id -> e.getComponents().stream()
                        .filter(c -> id.equals(c.getComponentId()))
                        .findFirst()
                        .map(EmployeeComponent::getOverrideAmount)
                        .orElse(null))
                .toList();
        return new EmployeeForm(e.getFirstName(), e.getLastName(), e.getNationalId(),
                e.getRssbNumber(), e.getTinNumber(), e.getEmail(), e.getPhone(), e.getJobTitle(),
                e.getDepartment(), e.getHireDate(), e.getEndDate(), e.getBasicSalary(),
                e.getCurrencyCode(), e.getPaymentMethod().name(), e.getBankName(),
                e.getBankAccount(), e.getMobileMoney(), e.getStatus().name(), e.getNotes(),
                e.isPensionMember(), e.isMaternityMember(), e.isMedicalMember(), e.isCbhiMember(),
                assigned, all, overrides);
    }

    private String nextEmployeeNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("EMPLOYEE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "EMP-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "EMP-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    public static EmployeeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EmployeeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return PaymentMethod.BANK_TRANSFER;
        }
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PaymentMethod.BANK_TRANSFER;
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

    public record EmployeeSummary(long all, long active, long onLeave, long suspended,
                                  long terminated, BigDecimal activeBasicTotal, long endingSoon) {}
}
