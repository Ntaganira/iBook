/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PayrollComponentService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Allowances and deductions that payslips can carry
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.PayrollComponentForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.PayrollComponent;
import com.ntaganira.heritier.ibook.enums.PayrollComponentCalculation;
import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.EmployeeRepository;
import com.ntaganira.heritier.ibook.repository.PayrollComponentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * The allowances and deductions a payslip can carry.
 *
 * <p>Both pages under Payroll are this one list filtered by kind, because an allowance and a
 * deduction are the same record with the sign reversed.
 *
 * <p>Every component needs an account. An allowance without one would have to fall back to the
 * general wages account, which quietly merges a transport allowance into salaries and makes it
 * impossible to answer what transport cost; a deduction without one has nowhere to put the money
 * it took off somebody's pay, and a payroll entry that cannot say where a deduction went does not
 * balance.
 */
@Service
public class PayrollComponentService {

    private static final String MODULE = "payroll";

    private final PayrollComponentRepository payrollComponentRepository;
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public PayrollComponentService(PayrollComponentRepository payrollComponentRepository,
                                   AccountRepository accountRepository,
                                   EmployeeRepository employeeRepository,
                                   AuditService auditService) {
        this.payrollComponentRepository = payrollComponentRepository;
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<PayrollComponent> list(PayrollComponentKind kind, String q, String active,
                                       Pageable pageable) {
        Boolean activeFlag = active == null || active.isBlank() ? null
                : "true".equalsIgnoreCase(active) || "active".equalsIgnoreCase(active);
        return payrollComponentRepository.search(kind, trimToNull(q), activeFlag, pageable);
    }

    @Transactional(readOnly = true)
    public PayrollComponent get(Long id) {
        return id == null ? null : payrollComponentRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PayrollComponent> active() {
        return payrollComponentRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<PayrollComponent> activeOf(PayrollComponentKind kind) {
        return payrollComponentRepository.findByKindAndActiveTrueOrderBySortOrderAscNameAsc(kind);
    }

    @Transactional(readOnly = true)
    public List<Account> accounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public ComponentSummary summary(PayrollComponentKind kind) {
        return new ComponentSummary(
                payrollComponentRepository.countByKind(kind),
                payrollComponentRepository.countByKindAndActive(kind, true),
                payrollComponentRepository.countByKindAndActive(kind, false));
    }

    /** How many people carry this component, which is what makes deleting it unsafe. */
    @Transactional(readOnly = true)
    public long usageCount(Long componentId) {
        if (componentId == null) {
            return 0;
        }
        return employeeRepository.findAll().stream()
                .filter(e -> e.getComponents().stream()
                        .anyMatch(c -> componentId.equals(c.getComponentId())))
                .count();
    }

    // ----- Editing -----

    @Transactional
    public PayrollComponent save(PayrollComponentForm form, Long id) {
        String code = trimToNull(form.code());
        if (code == null) {
            throw new IllegalArgumentException("A component needs a code");
        }
        boolean clash = id == null
                ? payrollComponentRepository.existsByCodeIgnoreCase(code)
                : payrollComponentRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (clash) {
            throw new IllegalArgumentException("Another component already uses the code " + code);
        }

        PayrollComponent component = id == null
                ? new PayrollComponent()
                : payrollComponentRepository.findById(id).orElseThrow();

        PayrollComponentKind kind = parseKind(form.kind());
        PayrollComponentCalculation calculation = parseCalculation(form.calculation());

        if (calculation == PayrollComponentCalculation.FIXED_AMOUNT
                && form.amountValue().signum() <= 0) {
            throw new IllegalArgumentException("A fixed component needs an amount above zero");
        }
        if (calculation == PayrollComponentCalculation.PERCENT_OF_BASIC
                && form.percentValue().signum() <= 0) {
            throw new IllegalArgumentException("A percentage component needs a percentage above zero");
        }

        Account account = form.accountId() == null ? null
                : accountRepository.findById(form.accountId()).orElse(null);
        if (account == null) {
            throw new IllegalArgumentException(kind == PayrollComponentKind.ALLOWANCE
                    ? "An allowance needs the expense account it is charged to"
                    : "A deduction needs the account the money is held in or recovered against");
        }

        component.setCode(code);
        component.setName(trimToNull(form.name()));
        component.setKind(kind);
        component.setCalculation(calculation);
        component.setAmount(calculation == PayrollComponentCalculation.FIXED_AMOUNT
                ? form.amountValue() : BigDecimal.ZERO);
        component.setPercent(calculation == PayrollComponentCalculation.PERCENT_OF_BASIC
                ? form.percentValue() : BigDecimal.ZERO);
        // A deduction is taken off pay that has already been taxed and counted, so neither flag
        // means anything for one; forcing them false keeps a stale tick out of a later query.
        component.setTaxable(kind == PayrollComponentKind.ALLOWANCE && form.taxableValue());
        component.setPensionable(kind == PayrollComponentKind.ALLOWANCE && form.pensionableValue());
        component.setAccountId(account.getId());
        component.setAccountCode(account.getCode());
        component.setAccountName(account.getName());
        component.setDescription(trimToNull(form.description()));
        component.setActive(form.activeValue());
        component.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());

        PayrollComponent saved = payrollComponentRepository.save(component);
        auditService.log(MODULE, id == null ? "CREATE_PAY_COMPONENT" : "UPDATE_PAY_COMPONENT",
                "payrollComponent#" + saved.getId(), saved.getCode() + " — " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        PayrollComponent component = payrollComponentRepository.findById(id).orElse(null);
        if (component == null) {
            return;
        }
        long inUse = usageCount(id);
        if (inUse > 0) {
            throw new IllegalStateException("This is on " + inUse
                    + (inUse == 1 ? " person" : " people") + ". Deactivate it instead — deleting it "
                    + "would leave their pay quietly short of what was agreed.");
        }
        payrollComponentRepository.delete(component);
        auditService.log(MODULE, "DELETE_PAY_COMPONENT", "payrollComponent#" + id,
                component.getCode() + " — " + component.getName());
    }

    @Transactional
    public PayrollComponent setActive(Long id, boolean active) {
        PayrollComponent component = payrollComponentRepository.findById(id).orElseThrow();
        component.setActive(active);
        PayrollComponent saved = payrollComponentRepository.save(component);
        auditService.log(MODULE, active ? "ACTIVATE_PAY_COMPONENT" : "DEACTIVATE_PAY_COMPONENT",
                "payrollComponent#" + id, saved.getCode() + " — " + saved.getName());
        return saved;
    }

    public PayrollComponentForm toForm(PayrollComponent c) {
        return new PayrollComponentForm(c.getCode(), c.getName(), c.getKind().name(),
                c.getCalculation().name(), c.getAmount(), c.getPercent(), c.getAccountId(),
                c.getDescription(), c.getSortOrder(), c.isTaxable(), c.isPensionable(), c.isActive());
    }

    public static PayrollComponentKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return PayrollComponentKind.ALLOWANCE;
        }
        try {
            return PayrollComponentKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PayrollComponentKind.ALLOWANCE;
        }
    }

    private static PayrollComponentCalculation parseCalculation(String calculation) {
        if (calculation == null || calculation.isBlank()) {
            return PayrollComponentCalculation.FIXED_AMOUNT;
        }
        try {
            return PayrollComponentCalculation.valueOf(calculation.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PayrollComponentCalculation.FIXED_AMOUNT;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ComponentSummary(long all, long active, long inactive) {}
}
