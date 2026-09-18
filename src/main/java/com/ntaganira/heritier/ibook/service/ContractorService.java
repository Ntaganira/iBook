/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ContractorService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Contractor register and the spend recorded against each one
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ContractorForm;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Contractor;
import com.ntaganira.heritier.ibook.entity.Expense;
import com.ntaganira.heritier.ibook.entity.Vendor;
import com.ntaganira.heritier.ibook.enums.ContractorRateType;
import com.ntaganira.heritier.ibook.enums.ContractorType;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.ExpenseStatus;
import com.ntaganira.heritier.ibook.repository.BillRepository;
import com.ntaganira.heritier.ibook.repository.ContractorRepository;
import com.ntaganira.heritier.ibook.repository.ExpenseRepository;
import com.ntaganira.heritier.ibook.repository.VendorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ContractorService {

    private static final String MODULE = "contractors";

    private final ContractorRepository contractorRepository;
    private final VendorRepository vendorRepository;
    private final BillRepository billRepository;
    private final ExpenseRepository expenseRepository;
    private final AuditService auditService;

    public ContractorService(ContractorRepository contractorRepository,
                             VendorRepository vendorRepository,
                             BillRepository billRepository,
                             ExpenseRepository expenseRepository,
                             AuditService auditService) {
        this.contractorRepository = contractorRepository;
        this.vendorRepository = vendorRepository;
        this.billRepository = billRepository;
        this.expenseRepository = expenseRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Contractor> list(String q, String type, String active, Pageable pageable) {
        Boolean activeFilter = null;
        if ("active".equalsIgnoreCase(active)) {
            activeFilter = Boolean.TRUE;
        } else if ("inactive".equalsIgnoreCase(active)) {
            activeFilter = Boolean.FALSE;
        }
        return contractorRepository.search(trimToNull(q), parseType(type), activeFilter, pageable);
    }

    @Transactional(readOnly = true)
    public Contractor get(Long id) {
        return id == null ? null : contractorRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Contractor> listActive() {
        return contractorRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return contractorRepository.findByNameIgnoreCase(name.trim())
                .filter(c -> !c.getId().equals(excludeId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public ContractorSummary summary() {
        LocalDate today = LocalDate.now();
        return new ContractorSummary(
                contractorRepository.count(),
                contractorRepository.countByActive(true),
                contractorRepository.countByContractorType(ContractorType.INDIVIDUAL),
                contractorRepository.countByContractorType(ContractorType.COMPANY),
                contractorRepository.countExpired(today),
                contractorRepository.countExpiringSoon(today, today.plusDays(30)));
    }

    /**
     * What has actually been spent with a contractor. Money runs through the linked supplier
     * record, so an unlinked contractor has no spend to show.
     */
    @Transactional(readOnly = true)
    public ContractorSpend spendFor(Contractor contractor) {
        List<SpendRow> rows = new ArrayList<>();
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal expensed = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        LocalDate last = null;

        if (contractor != null && contractor.getVendorId() != null) {
            for (Bill bill : billRepository.findByVendor(contractor.getVendorId())) {
                if (bill.getStatus() == DocumentStatus.DRAFT || bill.getStatus() == DocumentStatus.VOID) {
                    continue;
                }
                billed = billed.add(zero(bill.getTotal()));
                outstanding = outstanding.add(bill.getBalanceDue());
                last = laterOf(last, bill.getBillDate());
                rows.add(new SpendRow(bill.getBillDate(), "BILL", bill.getId(), bill.getBillNo(),
                        bill.getMemo(), zero(bill.getTotal()), bill.getBalanceDue(),
                        bill.getDisplayStatus().name(), bill.getCurrencyCode()));
            }
            for (Expense expense : expenseRepository.findByVendor(contractor.getVendorId())) {
                if (expense.getStatus() != ExpenseStatus.POSTED) {
                    continue;
                }
                expensed = expensed.add(zero(expense.getTotal()));
                last = laterOf(last, expense.getExpenseDate());
                rows.add(new SpendRow(expense.getExpenseDate(), "EXPENSE", expense.getId(),
                        expense.getExpenseNo(), expense.getMemo(), zero(expense.getTotal()),
                        BigDecimal.ZERO, expense.getStatus().name(), expense.getCurrencyCode()));
            }
        }

        rows.sort((a, b) -> {
            if (a.date() == null || b.date() == null) {
                return 0;
            }
            return b.date().compareTo(a.date());
        });

        BigDecimal total = billed.add(expensed);
        BigDecimal rate = contractor == null ? BigDecimal.ZERO : zero(contractor.getWithholdingRate());
        BigDecimal estimatedWithholding = total.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        return new ContractorSpend(rows, billed, expensed, total, outstanding,
                estimatedWithholding, last);
    }

    // ----- Create / update -----

    @Transactional
    public Contractor save(ContractorForm form, Long id) {
        Contractor contractor;
        if (id == null) {
            contractor = new Contractor();
        } else {
            contractor = contractorRepository.findById(id).orElseThrow();
        }

        contractor.setName(form.name().trim());
        contractor.setContractorType(parseTypeOrDefault(form.contractorType()));
        contractor.setTrade(trimToNull(form.trade()));
        contractor.setEmail(trimToNull(form.email()));
        contractor.setPhone(trimToNull(form.phone()));
        contractor.setMobileMoney(trimToNull(form.mobileMoney()));
        contractor.setTaxId(trimToNull(form.taxId()));
        contractor.setStreet(trimToNull(form.street()));
        contractor.setCity(trimToNull(form.city()));
        contractor.setCountry(trimToNull(form.country()));
        contractor.setBankName(trimToNull(form.bankName()));
        contractor.setBankAccount(trimToNull(form.bankAccount()));
        contractor.setContractStart(form.contractStart());
        contractor.setContractEnd(form.contractEnd());
        contractor.setRateType(parseRateOrDefault(form.rateType()));
        contractor.setRateAmount(zero(form.rateAmount()));
        contractor.setWithholdingRate(zero(form.withholdingRate()));
        contractor.setNotes(trimToNull(form.notes()));
        contractor.setActive(form.activeValue());

        Vendor vendor = form.vendorId() == null ? null
                : vendorRepository.findById(form.vendorId()).orElse(null);
        contractor.setVendorId(vendor == null ? null : vendor.getId());
        contractor.setVendorName(vendor == null ? null : vendor.getName());

        Contractor saved = contractorRepository.save(contractor);
        auditService.log(MODULE, id == null ? "CREATE_CONTRACTOR" : "UPDATE_CONTRACTOR",
                "contractor#" + saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void toggle(Long id) {
        Contractor contractor = contractorRepository.findById(id).orElse(null);
        if (contractor == null) {
            return;
        }
        contractor.setActive(!contractor.isActive());
        Contractor saved = contractorRepository.save(contractor);
        auditService.log(MODULE, saved.isActive() ? "ACTIVATE_CONTRACTOR" : "DEACTIVATE_CONTRACTOR",
                "contractor#" + id, saved.getName());
    }

    @Transactional
    public void delete(Long id) {
        Contractor contractor = contractorRepository.findById(id).orElse(null);
        if (contractor == null) {
            return;
        }
        contractorRepository.delete(contractor);
        auditService.log(MODULE, "DELETE_CONTRACTOR", "contractor#" + id, contractor.getName());
    }

    // ----- Helpers -----

    private static LocalDate laterOf(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static ContractorType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ContractorType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ContractorType parseTypeOrDefault(String type) {
        ContractorType parsed = parseType(type);
        return parsed == null ? ContractorType.INDIVIDUAL : parsed;
    }

    private static ContractorRateType parseRateOrDefault(String rateType) {
        if (rateType == null || rateType.isBlank()) {
            return ContractorRateType.FIXED;
        }
        try {
            return ContractorRateType.valueOf(rateType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ContractorRateType.FIXED;
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

    public record SpendRow(LocalDate date, String type, Long documentId, String documentNo,
                           String description, BigDecimal amount, BigDecimal outstanding,
                           String status, String currencyCode) {}

    public record ContractorSpend(List<SpendRow> rows, BigDecimal billed, BigDecimal expensed,
                                  BigDecimal total, BigDecimal outstanding,
                                  BigDecimal estimatedWithholding, LocalDate lastActivity) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public int count() {
            return rows.size();
        }
    }

    public record ContractorSummary(long all, long active, long individuals, long companies,
                                    long expired, long expiringSoon) {}
}
