/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : RecurringInvoiceService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Recurring invoice schedules and the invoices they raise
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.RecurringInvoiceForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus;
import com.ntaganira.heritier.ibook.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RecurringInvoiceService {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringInvoiceService.class);

    private static final String MODULE = "recurring-invoices";
    private static final String DEFAULT_REVENUE_CODE = "4002";

    /**
     * Ceiling on how many occurrences one schedule may catch up on in a single run, so a schedule
     * left dormant for years cannot flood the ledger in one pass.
     */
    private static final int MAX_CATCHUP = 24;

    private final RecurringInvoiceRepository recurringInvoiceRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final TaxRateRepository taxRateRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public RecurringInvoiceService(RecurringInvoiceRepository recurringInvoiceRepository,
                                   InvoiceRepository invoiceRepository,
                                   CustomerRepository customerRepository,
                                   AccountRepository accountRepository,
                                   ProductRepository productRepository,
                                   TaxRateRepository taxRateRepository,
                                   CompanyRepository companyRepository,
                                   InvoiceService invoiceService,
                                   AuditService auditService) {
        this.recurringInvoiceRepository = recurringInvoiceRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.taxRateRepository = taxRateRepository;
        this.companyRepository = companyRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<RecurringInvoice> list(String q, String status, Long customerId, Pageable pageable) {
        return recurringInvoiceRepository.search(trimToNull(q), parseStatus(status), customerId, pageable);
    }

    @Transactional(readOnly = true)
    public RecurringInvoice get(Long id) {
        return id == null ? null : recurringInvoiceRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return recurringInvoiceRepository.findByNameIgnoreCase(name.trim())
                .filter(r -> !r.getId().equals(excludeId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<Invoice> history(Long scheduleId) {
        return scheduleId == null ? List.of()
                : invoiceRepository.findByRecurringInvoiceIdOrderByIssueDateDescIdDesc(scheduleId);
    }

    @Transactional(readOnly = true)
    public RecurringSummary summary() {
        LocalDate today = LocalDate.now();
        return new RecurringSummary(
                recurringInvoiceRepository.count(),
                recurringInvoiceRepository.countByStatus(RecurringInvoiceStatus.DRAFT),
                recurringInvoiceRepository.countByStatus(RecurringInvoiceStatus.ACTIVE),
                recurringInvoiceRepository.countByStatus(RecurringInvoiceStatus.PAUSED),
                recurringInvoiceRepository.countByStatus(RecurringInvoiceStatus.COMPLETED),
                recurringInvoiceRepository.countByStatus(RecurringInvoiceStatus.CANCELLED),
                recurringInvoiceRepository.countDue(today),
                zero(recurringInvoiceRepository.totalFor(List.of(RecurringInvoiceStatus.ACTIVE))),
                monthlyValue());
    }

    /**
     * Active schedules restated as a monthly figure, so a mix of weekly and annual retainers can be
     * compared. It is an indication of committed revenue, not a ledger balance.
     */
    private BigDecimal monthlyValue() {
        BigDecimal monthly = BigDecimal.ZERO;
        for (RecurringInvoice r : recurringInvoiceRepository.findAll()) {
            if (r.getStatus() != RecurringInvoiceStatus.ACTIVE) {
                continue;
            }
            monthly = monthly.add(perMonth(zero(r.getTotal()), r.getFrequency()));
        }
        return monthly.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal perMonth(BigDecimal amount, RecurrenceFrequency frequency) {
        if (frequency == null) {
            return amount;
        }
        return switch (frequency) {
            case WEEKLY -> amount.multiply(new BigDecimal("52")).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            case FORTNIGHTLY -> amount.multiply(new BigDecimal("26")).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            case MONTHLY -> amount;
            case QUARTERLY -> amount.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            case SEMIANNUAL -> amount.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
            case ANNUAL -> amount.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        };
    }

    // ----- Create / update -----

    @Transactional
    public RecurringInvoice save(RecurringInvoiceForm form, Long id, String username) {
        RecurringInvoice schedule;
        if (id == null) {
            schedule = new RecurringInvoice();
            schedule.setCreatedBy(username);
            schedule.setStatus(RecurringInvoiceStatus.DRAFT);
        } else {
            schedule = recurringInvoiceRepository.findById(id).orElseThrow();
            if (!schedule.isEditable()) {
                throw new IllegalStateException("A cancelled schedule can no longer be edited");
            }
            schedule.getLines().clear();
        }

        Customer customer = customerRepository.findById(form.getCustomerId()).orElseThrow();
        schedule.setName(form.getName().trim());
        schedule.setCustomerId(customer.getId());
        schedule.setCustomerName(customer.getName());
        schedule.setCustomerEmail(customer.getEmail());
        schedule.setFrequency(parseFrequency(form.getFrequency()));
        schedule.setStartDate(form.getStartDate() == null ? LocalDate.now() : form.getStartDate());
        schedule.setEndDate(form.getEndDate());
        schedule.setMaxOccurrences(form.getMaxOccurrences() == null || form.getMaxOccurrences() <= 0
                ? null : form.getMaxOccurrences());
        schedule.setDueDays(form.dueDaysValue());
        schedule.setAutoPost(form.isAutoPost());
        schedule.setReference(trimToNull(form.getReference()));
        schedule.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        schedule.setCustomerMessage(trimToNull(form.getCustomerMessage()));
        schedule.setNotes(trimToNull(form.getNotes()));

        // Editing a schedule that has already billed must not rewind its place in the cycle.
        if (schedule.getOccurrencesGenerated() == 0) {
            schedule.setNextRunDate(schedule.getStartDate());
        }

        Account fallbackRevenue = accountRepository.findByCodeIgnoreCase(DEFAULT_REVENUE_CODE).orElse(null);
        int sort = 0;
        for (InvoiceForm.Line lineForm : form.filledLines()) {
            Product linked = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);
            TaxRate configured = lineForm.getTaxRateId() == null ? null
                    : taxRateRepository.findById(lineForm.getTaxRateId()).orElse(null);
            BigDecimal rate = configured != null ? zero(configured.getRate()) : lineForm.taxRateValue();
            Account revenue = lineForm.getRevenueAccountId() == null
                    ? fallbackRevenue
                    : accountRepository.findById(lineForm.getRevenueAccountId()).orElse(fallbackRevenue);
            BigDecimal base = lineForm.lineSubtotal();
            BigDecimal tax = taxOf(base, rate);
            schedule.addLine(RecurringInvoiceLine.builder()
                    .description(lineForm.getDescription().trim())
                    .productId(linked == null ? null : linked.getId())
                    .productSku(linked == null ? null : linked.getSku())
                    .quantity(lineForm.quantityValue())
                    .unitPrice(lineForm.unitPriceValue())
                    .taxRate(rate)
                    .taxRateId(configured == null ? null : configured.getId())
                    .lineSubtotal(base)
                    .lineTax(tax)
                    .lineTotal(base.add(tax))
                    .revenueAccountId(revenue == null ? null : revenue.getId())
                    .revenueAccountCode(revenue == null ? null : revenue.getCode())
                    .revenueAccountName(revenue == null ? null : revenue.getName())
                    .sortOrder(sort++)
                    .build());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (RecurringInvoiceLine l : schedule.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (RecurringInvoiceLine l : schedule.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            if (base.signum() == 0) {
                continue;
            }
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            taxTotal = taxTotal.add(taxOf(base.subtract(share), zero(l.getTaxRate())));
        }
        schedule.setSubtotal(subtotal);
        schedule.setDiscountAmount(discount);
        schedule.setTaxAmount(taxTotal);
        schedule.setTotal(subtotal.subtract(discount).add(taxTotal));

        RecurringInvoice saved = recurringInvoiceRepository.save(schedule);
        auditService.log(MODULE, id == null ? "CREATE_RECURRING" : "UPDATE_RECURRING",
                "recurringInvoice#" + saved.getId(), saved.getName() + " — " + saved.getCustomerName());

        if (form.isActivateNow() && saved.getStatus() == RecurringInvoiceStatus.DRAFT) {
            saved = activate(saved.getId());
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public RecurringInvoice activate(Long id) {
        RecurringInvoice schedule = recurringInvoiceRepository.findById(id).orElseThrow();
        if (schedule.getStatus() == RecurringInvoiceStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled schedule cannot be activated");
        }
        if (schedule.getLines().isEmpty()) {
            throw new IllegalStateException("A schedule with no line items cannot be activated");
        }
        if (schedule.isExhausted()) {
            throw new IllegalStateException("This schedule has already reached its end");
        }
        schedule.setStatus(RecurringInvoiceStatus.ACTIVE);
        schedule.setStoppedReason(null);
        if (schedule.getNextRunDate() == null) {
            schedule.setNextRunDate(schedule.getStartDate());
        }
        RecurringInvoice saved = recurringInvoiceRepository.save(schedule);
        auditService.log(MODULE, "ACTIVATE_RECURRING", "recurringInvoice#" + id,
                saved.getName() + " active from " + saved.getNextRunDate());
        return saved;
    }

    @Transactional
    public RecurringInvoice pause(Long id) {
        RecurringInvoice schedule = recurringInvoiceRepository.findById(id).orElseThrow();
        if (schedule.getStatus() != RecurringInvoiceStatus.ACTIVE) {
            return schedule;
        }
        schedule.setStatus(RecurringInvoiceStatus.PAUSED);
        RecurringInvoice saved = recurringInvoiceRepository.save(schedule);
        auditService.log(MODULE, "PAUSE_RECURRING", "recurringInvoice#" + id, saved.getName() + " paused");
        return saved;
    }

    @Transactional
    public RecurringInvoice cancel(Long id, String reason) {
        RecurringInvoice schedule = recurringInvoiceRepository.findById(id).orElseThrow();
        schedule.setStatus(RecurringInvoiceStatus.CANCELLED);
        schedule.setStoppedReason(trimToNull(reason));
        RecurringInvoice saved = recurringInvoiceRepository.save(schedule);
        auditService.log(MODULE, "CANCEL_RECURRING", "recurringInvoice#" + id,
                saved.getName() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        RecurringInvoice schedule = recurringInvoiceRepository.findById(id).orElse(null);
        if (schedule == null) {
            return;
        }
        if (schedule.getOccurrencesGenerated() > 0) {
            throw new IllegalStateException("A schedule that has already raised invoices cannot be deleted");
        }
        recurringInvoiceRepository.delete(schedule);
        auditService.log(MODULE, "DELETE_RECURRING", "recurringInvoice#" + id, schedule.getName());
    }

    // ----- Generation -----

    /**
     * Raises the next invoice by hand, whether or not the schedule is due. Used for the "generate
     * now" action, which bills an occurrence early and moves the cycle on.
     */
    @Transactional
    public Invoice generateNow(Long id, String username) {
        RecurringInvoice schedule = recurringInvoiceRepository.findById(id).orElseThrow();
        if (schedule.isStopped()) {
            throw new IllegalStateException("This schedule has stopped and cannot raise an invoice");
        }
        if (schedule.getLines().isEmpty()) {
            throw new IllegalStateException("A schedule with no line items cannot raise an invoice");
        }
        if (schedule.isExhausted()) {
            throw new IllegalStateException("This schedule has already reached its end");
        }
        Invoice invoice = generateOne(schedule, occurrenceDate(schedule), username);
        recurringInvoiceRepository.save(schedule);
        return invoice;
    }

    /**
     * Raises every occurrence that has fallen due, each dated the day it was owed rather than
     * today, so revenue lands in the period it belongs to even after the application has been down.
     */
    @Transactional
    public RunReport runDue(LocalDate today, String username) {
        List<RecurringInvoice> due = recurringInvoiceRepository.findDue(today);
        int schedules = 0;
        int invoices = 0;
        List<String> failures = new ArrayList<>();

        for (RecurringInvoice schedule : due) {
            if (schedule.getLines().isEmpty()) {
                failures.add(schedule.getName());
                continue;
            }
            int before = invoices;
            try {
                int guard = 0;
                while (schedule.getStatus() == RecurringInvoiceStatus.ACTIVE
                        && !schedule.isExhausted()
                        && schedule.getNextRunDate() != null
                        && !schedule.getNextRunDate().isAfter(today)
                        && guard < MAX_CATCHUP) {
                    generateOne(schedule, schedule.getNextRunDate(), username);
                    invoices++;
                    guard++;
                }
                recurringInvoiceRepository.save(schedule);
            } catch (RuntimeException ex) {
                LOG.warn("Recurring schedule {} failed to generate: {}", schedule.getName(), ex.getMessage());
                failures.add(schedule.getName());
            }
            if (invoices > before) {
                schedules++;
            }
        }
        return new RunReport(schedules, invoices, failures);
    }

    private LocalDate occurrenceDate(RecurringInvoice schedule) {
        return schedule.getNextRunDate() == null ? LocalDate.now() : schedule.getNextRunDate();
    }

    private Invoice generateOne(RecurringInvoice schedule, LocalDate occurrence, String username) {
        InvoiceForm form = new InvoiceForm();
        form.setCustomerId(schedule.getCustomerId());
        form.setIssueDate(occurrence);
        form.setDueDate(occurrence.plusDays(schedule.getDueDays()));
        form.setReference(schedule.getReference() == null ? schedule.getName() : schedule.getReference());
        form.setCurrencyCode(schedule.getCurrencyCode());
        form.setDiscountAmount(schedule.getDiscountAmount());
        form.setCustomerMessage(schedule.getCustomerMessage());
        form.setPostNow(false);

        for (int i = 0; i < schedule.getLines().size(); i++) {
            RecurringInvoiceLine src = schedule.getLines().get(i);
            InvoiceForm.Line line = form.getLines().get(i);
            line.setDescription(src.getDescription());
            line.setProductId(src.getProductId());
            line.setQuantity(src.getQuantity());
            line.setUnitPrice(src.getUnitPrice());
            line.setTaxRate(src.getTaxRate());
            line.setTaxRateId(src.getTaxRateId());
            line.setRevenueAccountId(src.getRevenueAccountId());
        }

        Invoice invoice = invoiceService.saveInvoice(form, null, username);
        invoice.setRecurringInvoiceId(schedule.getId());
        invoiceRepository.save(invoice);

        if (schedule.isAutoPost()) {
            invoice = invoiceService.postInvoice(invoice.getId());
        }

        schedule.setOccurrencesGenerated(schedule.getOccurrencesGenerated() + 1);
        schedule.setLastRunDate(LocalDate.now());
        schedule.setLastInvoiceId(invoice.getId());
        schedule.setLastInvoiceNo(invoice.getInvoiceNo());
        schedule.setNextRunDate(schedule.getFrequency().next(occurrence));
        if (schedule.isExhausted()) {
            schedule.setStatus(RecurringInvoiceStatus.COMPLETED);
        }

        auditService.log(MODULE, "GENERATE_RECURRING", "recurringInvoice#" + schedule.getId(),
                schedule.getName() + " → " + invoice.getInvoiceNo() + " (" + occurrence + ")");
        return invoice;
    }

    // ----- Helpers -----

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static RecurringInvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RecurringInvoiceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static RecurrenceFrequency parseFrequency(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return RecurrenceFrequency.MONTHLY;
        }
        try {
            return RecurrenceFrequency.valueOf(frequency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RecurrenceFrequency.MONTHLY;
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

    public record RunReport(int schedules, int invoices, List<String> failures) {
        public boolean isEmpty() {
            return invoices == 0;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }
    }

    public record RecurringSummary(long all, long draft, long active, long paused, long completed,
                                   long cancelled, long due, BigDecimal activeValue,
                                   BigDecimal monthlyValue) {}
}
