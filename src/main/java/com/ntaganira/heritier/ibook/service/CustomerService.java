/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : CustomerService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Customers domain service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.CustomerForm;
import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.repository.CustomerRepository;
import com.ntaganira.heritier.ibook.repository.InvoicePaymentRepository;
import com.ntaganira.heritier.ibook.repository.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerService {

    private static final String MODULE = "customers";

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           InvoiceRepository invoiceRepository,
                           InvoicePaymentRepository paymentRepository,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Customer> listCustomers(String q, String type, Pageable pageable) {
        return customerRepository.search(trimToNull(q), trimToNull(type), pageable);
    }

    @Transactional(readOnly = true)
    public List<Customer> listActiveCustomers() {
        return customerRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(Long id) {
        return customerRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? customerRepository.existsByNameIgnoreCase(name.trim())
                : customerRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional
    public Customer saveCustomer(CustomerForm form, Long id) {
        Customer customer = id == null ? new Customer() : customerRepository.findById(id).orElseThrow();
        customer.setName(form.name().trim());
        customer.setCompanyName(trimToNull(form.companyName()));
        customer.setEmail(trimToNull(form.email()));
        customer.setPhone(trimToNull(form.phone()));
        customer.setMobileMoney(trimToNull(form.mobileMoney()));
        customer.setTaxId(trimToNull(form.taxId()));
        customer.setStreet(trimToNull(form.street()));
        customer.setCity(trimToNull(form.city()));
        customer.setState(trimToNull(form.state()));
        customer.setPostalCode(trimToNull(form.postalCode()));
        customer.setCountry(trimToNull(form.country()));
        customer.setWebsite(trimToNull(form.website()));
        customer.setNotes(trimToNull(form.notes()));
        customer.setPaymentTerms(trimToNull(form.paymentTerms()));
        customer.setOpeningBalance(form.openingBalance() == null ? BigDecimal.ZERO : form.openingBalance());
        customer.setActive(form.active());
        Customer saved = customerRepository.save(customer);
        auditService.log(MODULE, id == null ? "CREATE_CUSTOMER" : "UPDATE_CUSTOMER",
                "customer#" + saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void toggleCustomer(Long id) {
        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return;
        }
        customer.setActive(!customer.isActive());
        customerRepository.save(customer);
        auditService.log(MODULE, "TOGGLE_CUSTOMER", "customer#" + id,
                customer.getName() + (customer.isActive() ? " activated" : " deactivated"));
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return;
        }
        if (!invoiceRepository.findByCustomer(id).isEmpty()) {
            throw new IllegalStateException("Customers with invoices cannot be deleted");
        }
        customerRepository.delete(customer);
        auditService.log(MODULE, "DELETE_CUSTOMER", "customer#" + id, customer.getName());
    }

    // ----- Reporting -----

    @Transactional(readOnly = true)
    public List<Invoice> invoicesFor(Long customerId) {
        return invoiceRepository.findByCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public CustomerStats statsFor(Long customerId) {
        List<Invoice> invoices = invoiceRepository.findByCustomer(customerId);
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        long open = 0;
        LocalDate last = null;
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == DocumentStatus.DRAFT || invoice.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            invoiced = invoiced.add(zero(invoice.getTotal()));
            paid = paid.add(zero(invoice.getAmountPaid()));
            if (invoice.getBalanceDue().signum() > 0) {
                open++;
            }
            if (last == null || (invoice.getIssueDate() != null && invoice.getIssueDate().isAfter(last))) {
                last = invoice.getIssueDate();
            }
        }
        return new CustomerStats(invoiced, paid, zero(invoiceRepository.customerBalance(customerId)),
                open, invoices.size(), last);
    }

    @Transactional(readOnly = true)
    public CustomerSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new CustomerSummary(
                customerRepository.count(),
                customerRepository.countByActiveTrue(),
                zero(invoiceRepository.totalOutstanding()),
                zero(invoiceRepository.totalOverdue(today)),
                zero(invoiceRepository.totalInvoicedBetween(monthStart, monthEnd)),
                zero(paymentRepository.collectedBetween(monthStart, monthEnd)));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CustomerStats(BigDecimal totalInvoiced,
                                BigDecimal totalPaid,
                                BigDecimal outstanding,
                                long openInvoices,
                                long invoiceCount,
                                LocalDate lastInvoiceDate) {
    }

    public record CustomerSummary(long total,
                                  long active,
                                  BigDecimal outstanding,
                                  BigDecimal overdue,
                                  BigDecimal invoicedThisMonth,
                                  BigDecimal collectedThisMonth) {
    }
}
