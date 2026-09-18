/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : VendorService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendors domain service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.VendorForm;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Vendor;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.repository.BillPaymentRepository;
import com.ntaganira.heritier.ibook.repository.BillRepository;
import com.ntaganira.heritier.ibook.repository.VendorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class VendorService {

    private static final String MODULE = "vendors";

    private final VendorRepository vendorRepository;
    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final AuditService auditService;

    public VendorService(VendorRepository vendorRepository,
                         BillRepository billRepository,
                         BillPaymentRepository billPaymentRepository,
                         AuditService auditService) {
        this.vendorRepository = vendorRepository;
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Vendor> listVendors(String q, String type, Pageable pageable) {
        return vendorRepository.search(trimToNull(q), trimToNull(type), pageable);
    }

    @Transactional(readOnly = true)
    public List<Vendor> listActiveVendors() {
        return vendorRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Vendor getVendor(Long id) {
        return vendorRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? vendorRepository.existsByNameIgnoreCase(name.trim())
                : vendorRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional
    public Vendor saveVendor(VendorForm form, Long id) {
        Vendor vendor = id == null ? new Vendor() : vendorRepository.findById(id).orElseThrow();
        vendor.setName(form.name().trim());
        vendor.setCompanyName(trimToNull(form.companyName()));
        vendor.setEmail(trimToNull(form.email()));
        vendor.setPhone(trimToNull(form.phone()));
        vendor.setMobileMoney(trimToNull(form.mobileMoney()));
        vendor.setTaxId(trimToNull(form.taxId()));
        vendor.setStreet(trimToNull(form.street()));
        vendor.setCity(trimToNull(form.city()));
        vendor.setState(trimToNull(form.state()));
        vendor.setPostalCode(trimToNull(form.postalCode()));
        vendor.setCountry(trimToNull(form.country()));
        vendor.setWebsite(trimToNull(form.website()));
        vendor.setNotes(trimToNull(form.notes()));
        vendor.setPaymentTerms(trimToNull(form.paymentTerms()));
        vendor.setOpeningBalance(form.openingBalance() == null ? BigDecimal.ZERO : form.openingBalance());
        vendor.setActive(form.active());
        Vendor saved = vendorRepository.save(vendor);
        auditService.log(MODULE, id == null ? "CREATE_VENDOR" : "UPDATE_VENDOR",
                "vendor#" + saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void toggleVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id).orElse(null);
        if (vendor == null) {
            return;
        }
        vendor.setActive(!vendor.isActive());
        vendorRepository.save(vendor);
        auditService.log(MODULE, "TOGGLE_VENDOR", "vendor#" + id,
                vendor.getName() + (vendor.isActive() ? " activated" : " deactivated"));
    }

    @Transactional
    public void deleteVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id).orElse(null);
        if (vendor == null) {
            return;
        }
        if (!billRepository.findByVendor(id).isEmpty()) {
            throw new IllegalStateException("Vendors with bills cannot be deleted");
        }
        vendorRepository.delete(vendor);
        auditService.log(MODULE, "DELETE_VENDOR", "vendor#" + id, vendor.getName());
    }

    // ----- Reporting -----

    @Transactional(readOnly = true)
    public List<Bill> billsFor(Long vendorId) {
        return billRepository.findByVendor(vendorId);
    }

    @Transactional(readOnly = true)
    public VendorStats statsFor(Long vendorId) {
        List<Bill> bills = billRepository.findByVendor(vendorId);
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        long open = 0;
        LocalDate last = null;
        for (Bill bill : bills) {
            if (bill.getStatus() == DocumentStatus.DRAFT || bill.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            billed = billed.add(zero(bill.getTotal()));
            paid = paid.add(zero(bill.getAmountPaid()));
            if (bill.getBalanceDue().signum() > 0) {
                open++;
            }
            if (last == null || (bill.getBillDate() != null && bill.getBillDate().isAfter(last))) {
                last = bill.getBillDate();
            }
        }
        return new VendorStats(billed, paid, zero(billRepository.vendorBalance(vendorId)),
                open, bills.size(), last);
    }

    @Transactional(readOnly = true)
    public VendorSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new VendorSummary(
                vendorRepository.count(),
                vendorRepository.countByActiveTrue(),
                zero(billRepository.totalOutstanding()),
                zero(billRepository.totalOverdue(today)),
                zero(billRepository.totalBilledBetween(monthStart, monthEnd)),
                zero(billPaymentRepository.paidBetween(monthStart, monthEnd)));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record VendorStats(BigDecimal totalBilled,
                              BigDecimal totalPaid,
                              BigDecimal outstanding,
                              long openBills,
                              long billCount,
                              LocalDate lastBillDate) {
    }

    public record VendorSummary(long total,
                                long active,
                                BigDecimal outstanding,
                                BigDecimal overdue,
                                BigDecimal billedThisMonth,
                                BigDecimal paidThisMonth) {
    }
}
