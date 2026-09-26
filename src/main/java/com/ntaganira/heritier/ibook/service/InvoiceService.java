/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : InvoiceService.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Sales invoice domain service with double-entry ledger posting
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.InvoicePaymentForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.enums.MovementType;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
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

@Service
public class InvoiceService {

    private static final String MODULE = "invoices";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String AR_ACCOUNT_CODE = "1201";
    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String COGS_ACCOUNT_CODE = "5200";
    private static final String COGS_FALLBACK_CODE = "5000";
    private static final String VAT_ACCOUNT_CODE = "2101";
    private static final String DEFAULT_REVENUE_CODE = "4002";

    private static final List<DocumentStatus> UNPAID_STATUSES =
            List.of(DocumentStatus.OPEN, DocumentStatus.PARTIALLY_PAID, DocumentStatus.OVERDUE);

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final TaxRateRepository taxRateRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ExciseDutyRepository exciseDutyRepository;
    private final AuditService auditService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoicePaymentRepository paymentRepository,
                          CustomerRepository customerRepository,
                          AccountRepository accountRepository,
                          JournalEntryRepository journalEntryRepository,
                          NumberingSequenceRepository numberingSequenceRepository,
                          CompanyRepository companyRepository,
                          TaxRateRepository taxRateRepository,
                          ProductRepository productRepository,
                          StockMovementRepository stockMovementRepository,
                          ExciseDutyRepository exciseDutyRepository,
                          AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.taxRateRepository = taxRateRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.exciseDutyRepository = exciseDutyRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Invoice> listInvoices(String q, String status, Long customerId,
                                      LocalDate from, LocalDate to, Pageable pageable) {
        DocumentStatus documentStatus = parseStatus(status);
        return invoiceRepository.search(trimToNull(q), documentStatus, customerId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(Long id) {
        return invoiceRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<InvoicePayment> paymentsFor(Long invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoiceId);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(Invoice invoice) {
        if (invoice == null || invoice.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(invoice.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public InvoiceSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new InvoiceSummary(
                zero(invoiceRepository.totalOutstanding()),
                zero(invoiceRepository.totalInvoicedBetween(monthStart, monthEnd)),
                zero(invoiceRepository.totalOverdue(today)),
                averageDaysToPay(),
                invoiceRepository.count(),
                invoiceRepository.countByStatus(DocumentStatus.DRAFT),
                invoiceRepository.countByStatus(DocumentStatus.OPEN),
                invoiceRepository.countByStatus(DocumentStatus.PARTIALLY_PAID),
                invoiceRepository.countByStatus(DocumentStatus.PAID),
                invoiceRepository.countOverdue(UNPAID_STATUSES, today));
    }

    private BigDecimal averageDaysToPay() {
        List<Invoice> paid = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() == DocumentStatus.PAID)
                .toList();
        if (paid.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long totalDays = 0;
        int counted = 0;
        for (Invoice invoice : paid) {
            List<InvoicePayment> payments = paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoice.getId());
            if (payments.isEmpty() || invoice.getIssueDate() == null) {
                continue;
            }
            LocalDate settled = payments.get(payments.size() - 1).getPaymentDate();
            totalDays += java.time.temporal.ChronoUnit.DAYS.between(invoice.getIssueDate(), settled);
            counted++;
        }
        if (counted == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalDays).divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP);
    }

    /**
     * Receivables aging as of a date, grouped by customer. Buckets are driven by how far
     * past its due date each open invoice is; anything not yet due sits in "current".
     */
    @Transactional(readOnly = true)
    public AgingReport aging(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        Map<Long, List<Invoice>> byCustomer = new LinkedHashMap<>();
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (!UNPAID_STATUSES.contains(invoice.getStatus()) || invoice.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (invoice.getIssueDate() != null && invoice.getIssueDate().isAfter(date)) {
                continue;
            }
            byCustomer.computeIfAbsent(invoice.getCustomerId(), k -> new ArrayList<>()).add(invoice);
        }

        List<AgingRow> rows = new ArrayList<>();
        BigDecimal tCurrent = BigDecimal.ZERO;
        BigDecimal t1 = BigDecimal.ZERO;
        BigDecimal t2 = BigDecimal.ZERO;
        BigDecimal t3 = BigDecimal.ZERO;
        BigDecimal t4 = BigDecimal.ZERO;

        for (Map.Entry<Long, List<Invoice>> entry : byCustomer.entrySet()) {
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal b1 = BigDecimal.ZERO;
            BigDecimal b2 = BigDecimal.ZERO;
            BigDecimal b3 = BigDecimal.ZERO;
            BigDecimal b4 = BigDecimal.ZERO;
            long oldest = 0;
            String name = null;
            for (Invoice invoice : entry.getValue()) {
                name = invoice.getCustomerName();
                BigDecimal due = invoice.getBalanceDue();
                long days = daysPastDue(invoice.getDueDate(), date);
                if (days > oldest) {
                    oldest = days;
                }
                if (days <= 0) {
                    current = current.add(due);
                } else if (days <= 30) {
                    b1 = b1.add(due);
                } else if (days <= 60) {
                    b2 = b2.add(due);
                } else if (days <= 90) {
                    b3 = b3.add(due);
                } else {
                    b4 = b4.add(due);
                }
            }
            BigDecimal total = current.add(b1).add(b2).add(b3).add(b4);
            rows.add(new AgingRow(entry.getKey(), name, current, b1, b2, b3, b4, total,
                    entry.getValue().size(), oldest));
            tCurrent = tCurrent.add(current);
            t1 = t1.add(b1);
            t2 = t2.add(b2);
            t3 = t3.add(b3);
            t4 = t4.add(b4);
        }

        rows.sort((a, b) -> b.total().compareTo(a.total()));
        BigDecimal grand = tCurrent.add(t1).add(t2).add(t3).add(t4);
        return new AgingReport(date, rows,
                new AgingTotals(tCurrent, t1, t2, t3, t4, grand));
    }

    private static long daysPastDue(LocalDate dueDate, LocalDate asOf) {
        if (dueDate == null || !dueDate.isBefore(asOf)) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, asOf);
    }

    /** Open invoices for one customer, oldest first — the drill-down under an aging row. */
    @Transactional(readOnly = true)
    public List<Invoice> openInvoicesFor(Long customerId, LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<Invoice> open = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByCustomer(customerId)) {
            if (!UNPAID_STATUSES.contains(invoice.getStatus()) || invoice.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (invoice.getIssueDate() != null && invoice.getIssueDate().isAfter(date)) {
                continue;
            }
            open.add(invoice);
        }
        open.sort((a, b) -> {
            LocalDate x = a.getDueDate() == null ? a.getIssueDate() : a.getDueDate();
            LocalDate y = b.getDueDate() == null ? b.getIssueDate() : b.getDueDate();
            if (x == null || y == null) {
                return 0;
            }
            return x.compareTo(y);
        });
        return open;
    }

    public record AgingRow(Long customerId, String customerName,
                           BigDecimal current, BigDecimal d1to30, BigDecimal d31to60,
                           BigDecimal d61to90, BigDecimal d90plus, BigDecimal total,
                           long invoiceCount, long oldestDays) {}

    public record AgingTotals(BigDecimal current, BigDecimal d1to30, BigDecimal d31to60,
                              BigDecimal d61to90, BigDecimal d90plus, BigDecimal total) {}

    public record AgingReport(LocalDate asOf, List<AgingRow> rows, AgingTotals totals) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public Map<String, List<Invoice>> agingBuckets() {
        LocalDate today = LocalDate.now();
        Map<String, List<Invoice>> buckets = new LinkedHashMap<>();
        buckets.put("current", new ArrayList<>());
        buckets.put("d1to30", new ArrayList<>());
        buckets.put("d31to60", new ArrayList<>());
        buckets.put("d61to90", new ArrayList<>());
        buckets.put("d90plus", new ArrayList<>());
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (!UNPAID_STATUSES.contains(invoice.getStatus()) || invoice.getBalanceDue().signum() <= 0) {
                continue;
            }
            long days = invoice.getDueDate() == null || !invoice.getDueDate().isBefore(today)
                    ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            if (days <= 0) {
                buckets.get("current").add(invoice);
            } else if (days <= 30) {
                buckets.get("d1to30").add(invoice);
            } else if (days <= 60) {
                buckets.get("d31to60").add(invoice);
            } else if (days <= 90) {
                buckets.get("d61to90").add(invoice);
            } else {
                buckets.get("d90plus").add(invoice);
            }
        }
        return buckets;
    }

    // ----- Create / update -----

    @Transactional
    public Invoice saveInvoice(InvoiceForm form, Long id, String username) {
        Invoice invoice;
        if (id == null) {
            invoice = new Invoice();
            invoice.setInvoiceNo(nextInvoiceNo());
            invoice.setCreatedBy(username);
            invoice.setStatus(DocumentStatus.DRAFT);
        } else {
            invoice = invoiceRepository.findById(id).orElseThrow();
            if (!invoice.isEditable()) {
                throw new IllegalStateException("Only draft invoices can be edited");
            }
            invoice.getLines().clear();
        }

        Customer customer = customerRepository.findById(form.getCustomerId()).orElseThrow();
        invoice.setCustomerId(customer.getId());
        invoice.setCustomerName(customer.getName());
        invoice.setCustomerEmail(customer.getEmail());
        invoice.setIssueDate(form.getIssueDate() == null ? LocalDate.now() : form.getIssueDate());
        invoice.setDueDate(form.getDueDate() == null ? invoice.getIssueDate().plusDays(30) : form.getDueDate());
        invoice.setPaymentTerms(trimToNull(form.getPaymentTerms()));
        invoice.setReference(trimToNull(form.getReference()));
        invoice.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        invoice.setCustomerMessage(trimToNull(form.getCustomerMessage()));
        invoice.setNotes(trimToNull(form.getNotes()));

        Account fallbackRevenue = accountRepository.findByCodeIgnoreCase(DEFAULT_REVENUE_CODE).orElse(null);
        int order = 0;
        for (InvoiceForm.Line lineForm : form.filledLines()) {
            Account revenue = lineForm.getRevenueAccountId() == null
                    ? fallbackRevenue
                    : accountRepository.findById(lineForm.getRevenueAccountId()).orElse(fallbackRevenue);
            ResolvedTax resolved = resolveTax(lineForm.getTaxRateId(), lineForm.taxRateValue());
            Product linked = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);

            /*
             * Excise, where the product carries a confirmed duty. It is added to the net BEFORE
             * VAT, because excise forms part of the value VAT is charged on: charging both on the
             * net would understate VAT, and charging excise on the VAT-inclusive figure would
             * overstate the duty. An unconfirmed duty is never applied, so a rate nobody has read
             * against the current schedule cannot reach an invoice.
             */
            ExciseDuty duty = linked == null || linked.getExciseDutyId() == null ? null
                    : exciseDutyRepository.findById(linked.getExciseDutyId()).orElse(null);
            BigDecimal excise = duty == null || !duty.isUsable()
                    ? BigDecimal.ZERO
                    : duty.on(lineForm.lineSubtotal(), lineForm.quantityValue());
            BigDecimal taxBase = lineForm.lineSubtotal().add(excise);

            InvoiceLine line = InvoiceLine.builder()
                    .description(lineForm.getDescription().trim())
                    .quantity(lineForm.quantityValue())
                    .unitPrice(lineForm.unitPriceValue())
                    .taxRate(resolved.rate())
                    .taxRateId(resolved.rateId())
                    .productId(linked == null ? null : linked.getId())
                    .productSku(linked == null ? null : linked.getSku())
                    .taxTreatment(resolved.treatment())
                    .lineSubtotal(lineForm.lineSubtotal())
                    .exciseDutyId(excise.signum() == 0 ? null : duty.getId())
                    .exciseCode(excise.signum() == 0 ? null : duty.getCode())
                    .exciseAmount(excise)
                    .lineTax(taxOf(taxBase, resolved.rate()))
                    .lineTotal(taxBase.add(taxOf(taxBase, resolved.rate())))
                    .revenueAccountId(revenue == null ? null : revenue.getId())
                    .revenueAccountCode(revenue == null ? null : revenue.getCode())
                    .revenueAccountName(revenue == null ? null : revenue.getName())
                    .sortOrder(order++)
                    .build();
            invoice.addLine(line);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (var l : invoice.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal exciseTotal = BigDecimal.ZERO;
        for (var l : invoice.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            BigDecimal excise = zero(l.getExciseAmount());
            exciseTotal = exciseTotal.add(excise);
            if (base.signum() == 0 && excise.signum() == 0) {
                continue;
            }
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            // A discount reduces the value VAT is charged on; the duty does not move with it,
            // because the duty was charged on the goods rather than on what was agreed for them.
            taxTotal = taxTotal.add(taxOf(base.subtract(share).add(excise), zero(l.getTaxRate())));
        }
        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discount);
        invoice.setExciseTotal(exciseTotal);
        invoice.setTaxAmount(taxTotal);
        invoice.setTotal(subtotal.subtract(discount).add(exciseTotal).add(taxTotal));

        Invoice saved = invoiceRepository.save(invoice);
        auditService.log(MODULE, id == null ? "CREATE_INVOICE" : "UPDATE_INVOICE",
                "invoice#" + saved.getId(), saved.getInvoiceNo() + " — " + saved.getCustomerName());

        if (form.isPostNow()) {
            saved = postInvoice(saved.getId());
        }
        return saved;
    }

    // ----- Posting to the general ledger -----

    @Transactional
    public Invoice postInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElseThrow();
        if (invoice.isPosted()) {
            return invoice;
        }
        if (invoice.getStatus() == DocumentStatus.VOID) {
            throw new IllegalStateException("A void invoice cannot be posted");
        }
        if (invoice.getLines().isEmpty()) {
            throw new IllegalStateException("An invoice with no line items cannot be posted");
        }

        Account receivable = requireAccount(AR_ACCOUNT_CODE, "Accounts receivable");
        Account vatPayable = accountRepository.findByCodeIgnoreCase(VAT_ACCOUNT_CODE).orElse(null);

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(invoice.getIssueDate())
                .type(JournalEntryType.INVOICE)
                .status(JournalEntryStatus.POSTED)
                .reference(invoice.getInvoiceNo())
                .memo("Sales invoice " + invoice.getInvoiceNo() + " — " + invoice.getCustomerName())
                .createdBy(AuditService.currentUsername())
                .build();

        int order = 0;
        entry.addLine(JournalLine.builder()
                .accountId(receivable.getId())
                .accountCode(receivable.getCode())
                .accountName(receivable.getName())
                .memo(invoice.getCustomerName())
                .debit(invoice.getTotal())
                .credit(BigDecimal.ZERO)
                .sortOrder(order++)
                .build());

        BigDecimal subtotal = zero(invoice.getSubtotal());
        BigDecimal discount = zero(invoice.getDiscountAmount());
        Map<Long, BigDecimal> revenueByAccount = new LinkedHashMap<>();
        Map<Long, Account> accountCache = new LinkedHashMap<>();

        for (InvoiceLine line : invoice.getLines()) {
            if (line.getRevenueAccountId() == null) {
                continue;
            }
            BigDecimal base = zero(line.getLineSubtotal());
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            BigDecimal net = base.subtract(share);
            revenueByAccount.merge(line.getRevenueAccountId(), net, BigDecimal::add);
            accountCache.computeIfAbsent(line.getRevenueAccountId(),
                    key -> accountRepository.findById(key).orElse(null));
        }

        BigDecimal creditedRevenue = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> revenue : revenueByAccount.entrySet()) {
            Account account = accountCache.get(revenue.getKey());
            if (account == null || revenue.getValue().signum() == 0) {
                continue;
            }
            creditedRevenue = creditedRevenue.add(revenue.getValue());
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(invoice.getInvoiceNo())
                    .debit(BigDecimal.ZERO)
                    .credit(revenue.getValue())
                    .sortOrder(order++)
                    .build());
        }

        /*
         * Excise is credited to a LIABILITY, never to income: the company collects it from the
         * customer on the state's behalf and it was never the company's to earn. Grouped by the
         * account each duty names, so two duties held in different accounts stay apart.
         */
        BigDecimal creditedExcise = BigDecimal.ZERO;
        Map<Long, BigDecimal> exciseByAccount = new LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            BigDecimal amount = zero(line.getExciseAmount());
            if (amount.signum() == 0 || line.getExciseDutyId() == null) {
                continue;
            }
            ExciseDuty duty = exciseDutyRepository.findById(line.getExciseDutyId()).orElse(null);
            if (duty == null || duty.getPayableAccountId() == null) {
                continue;
            }
            exciseByAccount.merge(duty.getPayableAccountId(), amount, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> excise : exciseByAccount.entrySet()) {
            Account account = accountRepository.findById(excise.getKey()).orElse(null);
            if (account == null || excise.getValue().signum() == 0) {
                continue;
            }
            creditedExcise = creditedExcise.add(excise.getValue());
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo("Excise duty on " + invoice.getInvoiceNo())
                    .debit(BigDecimal.ZERO)
                    .credit(excise.getValue())
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal tax = zero(invoice.getTaxAmount());
        if (tax.signum() != 0 && vatPayable != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(vatPayable.getId())
                    .accountCode(vatPayable.getCode())
                    .accountName(vatPayable.getName())
                    .memo("VAT on " + invoice.getInvoiceNo())
                    .debit(BigDecimal.ZERO)
                    .credit(tax)
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal rounding = invoice.getTotal().subtract(creditedRevenue)
                .subtract(creditedExcise).subtract(tax);
        if (rounding.signum() != 0 && !entry.getLines().isEmpty()) {
            JournalLine last = entry.getLines().get(entry.getLines().size() - 1);
            last.setCredit(last.getCreditValue().add(rounding));
        }

        // Perpetual inventory: stocked lines also move cost out of inventory into cost of sales.
        BigDecimal costOfSales = BigDecimal.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            Product product = line.getProductId() == null ? null
                    : productRepository.findById(line.getProductId()).orElse(null);
            if (product == null || !product.isTrackStock()) {
                continue;
            }
            costOfSales = costOfSales.add(zero(line.getQuantity()).multiply(zero(product.getCostPrice())));
        }
        Account inventoryAccount = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        Account cogsAccount = accountRepository.findByCodeIgnoreCase(COGS_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(COGS_FALLBACK_CODE)).orElse(null);
        if (costOfSales.signum() != 0 && inventoryAccount != null && cogsAccount != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(cogsAccount.getId())
                    .accountCode(cogsAccount.getCode())
                    .accountName(cogsAccount.getName())
                    .memo("Cost of sales " + invoice.getInvoiceNo())
                    .debit(costOfSales)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
            entry.addLine(JournalLine.builder()
                    .accountId(inventoryAccount.getId())
                    .accountCode(inventoryAccount.getCode())
                    .accountName(inventoryAccount.getName())
                    .memo("Stock out " + invoice.getInvoiceNo())
                    .debit(BigDecimal.ZERO)
                    .credit(costOfSales)
                    .sortOrder(order)
                    .build());
        }

        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        recordStockMovements(invoice, savedEntry.getId());

        invoice.setJournalEntryId(savedEntry.getId());
        invoice.setStatus(zero(invoice.getAmountPaid()).signum() > 0
                ? resolvePaidStatus(invoice) : DocumentStatus.OPEN);
        Invoice saved = invoiceRepository.save(invoice);

        auditService.log(MODULE, "POST_INVOICE", "invoice#" + saved.getId(),
                saved.getInvoiceNo() + " posted as " + savedEntry.getEntryNo());
        return saved;
    }

    // ----- Payments -----

    @Transactional
    public InvoicePayment recordPayment(Long invoiceId, InvoicePaymentForm form) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        if (invoice.getStatus() == DocumentStatus.VOID) {
            throw new IllegalStateException("A void invoice cannot receive payments");
        }
        if (!invoice.isPosted()) {
            throw new IllegalStateException("Post the invoice before recording a payment");
        }
        BigDecimal amount = form.amount() == null ? BigDecimal.ZERO : form.amount();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Payment exceeds the outstanding balance");
        }

        Account deposit = accountRepository.findById(form.depositAccountId()).orElseThrow();
        Account receivable = requireAccount(AR_ACCOUNT_CODE, "Accounts receivable");
        LocalDate paymentDate = form.paymentDate() == null ? LocalDate.now() : form.paymentDate();

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(paymentDate)
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(invoice.getInvoiceNo())
                .memo("Payment received for " + invoice.getInvoiceNo() + " — " + invoice.getCustomerName())
                .createdBy(AuditService.currentUsername())
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(deposit.getId())
                .accountCode(deposit.getCode())
                .accountName(deposit.getName())
                .memo(invoice.getInvoiceNo())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(receivable.getId())
                .accountCode(receivable.getCode())
                .accountName(receivable.getName())
                .memo(invoice.getCustomerName())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(amount);
        entry.setTotalCredits(amount);
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        InvoicePayment payment = InvoicePayment.builder()
                .invoice(invoice)
                .paymentDate(paymentDate)
                .amount(amount)
                .method(parseMethod(form.method()))
                .reference(trimToNull(form.reference()))
                .depositAccountId(deposit.getId())
                .depositAccountCode(deposit.getCode())
                .depositAccountName(deposit.getName())
                .journalEntryId(savedEntry.getId())
                .notes(trimToNull(form.notes()))
                .createdBy(AuditService.currentUsername())
                .build();
        InvoicePayment savedPayment = paymentRepository.save(payment);

        invoice.setAmountPaid(zero(invoice.getAmountPaid()).add(amount));
        invoice.setStatus(resolvePaidStatus(invoice));
        invoiceRepository.save(invoice);

        auditService.log(MODULE, "RECORD_PAYMENT", "invoice#" + invoice.getId(),
                invoice.getInvoiceNo() + " received " + amount.toPlainString());
        return savedPayment;
    }

    private DocumentStatus resolvePaidStatus(Invoice invoice) {
        return invoice.getSettlementStatus();
    }

    // ----- Payments received -----

    /** Payments in a period, newest first, optionally narrowed to one method. */
    @Transactional(readOnly = true)
    public PaymentsReport paymentsReceived(LocalDate from, LocalDate to, String method) {
        PaymentMethod filter = parseMethodOrNull(method);
        List<PaymentRow> rows = new ArrayList<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> byAccount = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (InvoicePayment payment : paymentRepository.findBetween(from, to)) {
            if (filter != null && payment.getMethod() != filter) {
                continue;
            }
            Invoice invoice = payment.getInvoice();
            BigDecimal amount = zero(payment.getAmount());
            rows.add(new PaymentRow(
                    payment.getId(),
                    payment.getPaymentDate(),
                    amount,
                    payment.getMethod() == null ? null : payment.getMethod().name(),
                    payment.getReference(),
                    payment.getDepositAccountCode(),
                    payment.getDepositAccountName(),
                    payment.getJournalEntryId(),
                    invoice == null ? null : invoice.getId(),
                    invoice == null ? null : invoice.getInvoiceNo(),
                    invoice == null ? null : invoice.getCustomerId(),
                    invoice == null ? null : invoice.getCustomerName(),
                    invoice == null ? null : invoice.getCurrencyCode()));
            total = total.add(amount);
            byMethod.merge(payment.getMethod() == null ? "UNKNOWN" : payment.getMethod().name(),
                    amount, BigDecimal::add);
            byAccount.merge(payment.getDepositAccountCode() == null
                            ? "-" : payment.getDepositAccountCode() + " - " + payment.getDepositAccountName(),
                    amount, BigDecimal::add);
        }
        return new PaymentsReport(from, to, rows, total, byMethod, byAccount);
    }

    private static PaymentMethod parseMethodOrNull(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record PaymentRow(Long id, LocalDate date, BigDecimal amount, String method,
                             String reference, String accountCode, String accountName,
                             Long journalEntryId, Long documentId, String documentNo,
                             Long partyId, String partyName, String currencyCode) {}

    public record PaymentsReport(LocalDate from, LocalDate to, List<PaymentRow> rows,
                                 BigDecimal total, Map<String, BigDecimal> byMethod,
                                 Map<String, BigDecimal> byAccount) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public int count() {
            return rows.size();
        }
    }

    // ----- Customer statement -----

    /**
     * Statement of account for one party: balance brought forward at the period start,
     * then every charge and payment in date order with a running balance.
     */
    @Transactional(readOnly = true)
    public Statement statementFor(Long partyId, LocalDate from, LocalDate to) {
        List<Invoice> docs = invoiceRepository.findByCustomer(partyId);
        List<InvoicePayment> payments = paymentRepository.findByCustomer(partyId);

        BigDecimal opening = BigDecimal.ZERO;
        String name = null;
        String currency = null;

        for (Invoice d : docs) {
            if (d.getStatus() == DocumentStatus.DRAFT || d.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            name = d.getCustomerName();
            if (currency == null) {
                currency = d.getCurrencyCode();
            }
            if (d.getIssueDate() != null && d.getIssueDate().isBefore(from)) {
                opening = opening.add(zero(d.getTotal()));
            }
        }
        for (InvoicePayment p : payments) {
            if (p.getPaymentDate() != null && p.getPaymentDate().isBefore(from)) {
                opening = opening.subtract(zero(p.getAmount()));
            }
        }

        List<StatementLine> lines = new ArrayList<>();
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;

        for (Invoice d : docs) {
            if (d.getStatus() == DocumentStatus.DRAFT || d.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            LocalDate date = d.getIssueDate();
            if (date == null || date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            charges = charges.add(zero(d.getTotal()));
            lines.add(new StatementLine(date, "INVOICE", d.getId(), d.getInvoiceNo(),
                    d.getReference(), zero(d.getTotal()), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (InvoicePayment p : payments) {
            LocalDate date = p.getPaymentDate();
            if (date == null || date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            credits = credits.add(zero(p.getAmount()));
            lines.add(new StatementLine(date, "PAYMENT", null, null,
                    p.getReference(), BigDecimal.ZERO, zero(p.getAmount()), BigDecimal.ZERO));
        }

        lines.sort((a, b) -> {
            int byDate = a.date().compareTo(b.date());
            // A charge precedes a payment on the same day so the running balance reads naturally.
            return byDate != 0 ? byDate : Integer.compare(
                    "PAYMENT".equals(a.type()) ? 1 : 0, "PAYMENT".equals(b.type()) ? 1 : 0);
        });

        List<StatementLine> withBalance = new ArrayList<>();
        BigDecimal running = opening;
        for (StatementLine l : lines) {
            running = running.add(l.charge()).subtract(l.payment());
            withBalance.add(new StatementLine(l.date(), l.type(), l.documentId(), l.documentNo(),
                    l.reference(), l.charge(), l.payment(), running));
        }

        return new Statement(partyId, name, currency, from, to, opening, withBalance,
                charges, credits, running);
    }

    public record StatementLine(LocalDate date, String type, Long documentId, String documentNo,
                                String reference, BigDecimal charge, BigDecimal payment,
                                BigDecimal balance) {}

    public record Statement(Long partyId, String partyName, String currencyCode,
                            LocalDate from, LocalDate to, BigDecimal openingBalance,
                            List<StatementLine> lines, BigDecimal totalCharges,
                            BigDecimal totalPayments, BigDecimal closingBalance) {
        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    // ----- Collections worklist -----

    /**
     * Overdue invoices ordered by exposure — the oldest, largest debts first — with the
     * contact details and last-payment date needed to chase them.
     */
    @Transactional(readOnly = true)
    public CollectionsReport collections(LocalDate asOf, int minDays, Long customerId) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        Map<Long, LocalDate> lastPayment = new LinkedHashMap<>();
        for (InvoicePayment payment : paymentRepository.findAll()) {
            Invoice inv = payment.getInvoice();
            if (inv == null || payment.getPaymentDate() == null) {
                continue;
            }
            LocalDate seen = lastPayment.get(inv.getCustomerId());
            if (seen == null || payment.getPaymentDate().isAfter(seen)) {
                lastPayment.put(inv.getCustomerId(), payment.getPaymentDate());
            }
        }

        Map<Long, Customer> customers = new LinkedHashMap<>();
        for (Customer c : customerRepository.findAll()) {
            customers.put(c.getId(), c);
        }

        List<CollectionRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal worst = BigDecimal.ZERO;
        long oldest = 0;

        for (Invoice invoice : invoiceRepository.findAll()) {
            if (!UNPAID_STATUSES.contains(invoice.getStatus()) || invoice.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (customerId != null && !customerId.equals(invoice.getCustomerId())) {
                continue;
            }
            if (invoice.getDueDate() == null || !invoice.getDueDate().isBefore(date)) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), date);
            if (days < minDays) {
                continue;
            }
            Customer c = customers.get(invoice.getCustomerId());
            BigDecimal due = invoice.getBalanceDue();
            rows.add(new CollectionRow(
                    invoice.getId(), invoice.getInvoiceNo(), invoice.getCurrencyCode(),
                    invoice.getCustomerId(), invoice.getCustomerName(),
                    invoice.getCustomerEmail() != null ? invoice.getCustomerEmail()
                            : (c == null ? null : c.getEmail()),
                    c == null ? null : c.getPhone(),
                    invoice.getIssueDate(), invoice.getDueDate(), days,
                    zero(invoice.getTotal()), due,
                    lastPayment.get(invoice.getCustomerId()),
                    bucketOf(days)));
            total = total.add(due);
            if (due.compareTo(worst) > 0) {
                worst = due;
            }
            if (days > oldest) {
                oldest = days;
            }
        }

        // Oldest first, then largest — that is the order a collections clerk works in.
        rows.sort((a, b) -> {
            int byAge = Long.compare(b.daysOverdue(), a.daysOverdue());
            return byAge != 0 ? byAge : b.balanceDue().compareTo(a.balanceDue());
        });

        return new CollectionsReport(date, rows, total, worst, oldest, minDays);
    }

    private static String bucketOf(long days) {
        if (days <= 30) {
            return "d1to30";
        }
        if (days <= 60) {
            return "d31to60";
        }
        if (days <= 90) {
            return "d61to90";
        }
        return "d90plus";
    }

    public record CollectionRow(Long invoiceId, String invoiceNo, String currencyCode,
                                Long customerId, String customerName, String customerEmail,
                                String customerPhone, LocalDate issueDate, LocalDate dueDate,
                                long daysOverdue, BigDecimal total, BigDecimal balanceDue,
                                LocalDate lastPaymentDate, String bucket) {}

    public record CollectionsReport(LocalDate asOf, List<CollectionRow> rows, BigDecimal totalOverdue,
                                    BigDecimal largest, long oldestDays, int minDays) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public int count() {
            return rows.size();
        }
    }

    // ----- Lifecycle -----

    @Transactional
    public Invoice markSent(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElseThrow();
        if (!invoice.isPosted()) {
            postInvoice(id);
            invoice = invoiceRepository.findById(id).orElseThrow();
        }
        invoice.setSentAt(java.time.LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);
        auditService.log(MODULE, "SEND_INVOICE", "invoice#" + id, saved.getInvoiceNo() + " marked as sent");
        return saved;
    }

    @Transactional
    public Invoice voidInvoice(Long id, String reason) {
        Invoice invoice = invoiceRepository.findById(id).orElseThrow();
        if (invoice.getStatus() == DocumentStatus.VOID) {
            return invoice;
        }
        if (zero(invoice.getAmountPaid()).signum() > 0) {
            throw new IllegalStateException("An invoice with recorded payments cannot be voided");
        }
        if (invoice.isPosted()) {
            JournalEntry original = journalEntryRepository.findById(invoice.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(invoice.getInvoiceNo())
                        .memo("Reversal of " + original.getEntryNo() + " — voided invoice "
                                + invoice.getInvoiceNo())
                        .createdBy(AuditService.currentUsername())
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
        invoice.setStatus(DocumentStatus.VOID);
        Invoice saved = invoiceRepository.save(invoice);
        auditService.log(MODULE, "VOID_INVOICE", "invoice#" + id,
                saved.getInvoiceNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void deleteDraft(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElse(null);
        if (invoice == null) {
            return;
        }
        if (!invoice.isEditable()) {
            throw new IllegalStateException("Only draft invoices can be deleted");
        }
        invoiceRepository.delete(invoice);
        auditService.log(MODULE, "DELETE_INVOICE", "invoice#" + id, invoice.getInvoiceNo() + " draft deleted");
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("INVOICE").orElse(null);
        return seq == null ? "INV-0001" : seq.previewNext();
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    private String nextInvoiceNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("INVOICE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "INV-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "INV-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private Account requireAccount(String code, String label) {
        return accountRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Chart of accounts is missing " + label + " (" + code + ")"));
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

    private static DocumentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return PaymentMethod.BANK_TRANSFER;
        }
        try {
            return PaymentMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PaymentMethod.BANK_TRANSFER;
        }
    }

    /** A line may carry a configured rate id, or a raw percentage from an older draft. */
    private ResolvedTax resolveTax(Long taxRateId, BigDecimal fallbackRate) {
        if (taxRateId != null) {
            TaxRate configured = taxRateRepository.findById(taxRateId).orElse(null);
            if (configured != null) {
                return new ResolvedTax(configured.getId(), zero(configured.getRate()), configured.getTreatment());
            }
        }
        BigDecimal rate = zero(fallbackRate);
        return new ResolvedTax(null, rate,
                rate.signum() > 0 ? TaxTreatment.STANDARD : TaxTreatment.ZERO_RATED);
    }

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private record ResolvedTax(Long rateId, BigDecimal rate, TaxTreatment treatment) {}

    /** Stocked lines leave inventory when the invoice is posted. */
    private void recordStockMovements(Invoice invoice, Long journalEntryId) {
        for (InvoiceLine line : invoice.getLines()) {
            Product product = line.getProductId() == null ? null
                    : productRepository.findById(line.getProductId()).orElse(null);
            if (product == null || !product.isTrackStock()) {
                continue;
            }
            BigDecimal qty = zero(line.getQuantity());
            if (qty.signum() == 0) {
                continue;
            }
            stockMovementRepository.save(StockMovement.builder()
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .movementType(MovementType.SALE)
                    .quantity(qty)
                    .unitCost(zero(product.getCostPrice()))
                    .movementDate(invoice.getIssueDate())
                    .reference(invoice.getInvoiceNo())
                    .notes(invoice.getCustomerName())
                    .journalEntryId(journalEntryId)
                    .createdBy(AuditService.currentUsername())
                    .build());
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

    public record InvoiceSummary(BigDecimal outstanding,
                                 BigDecimal invoicedThisMonth,
                                 BigDecimal overdueAmount,
                                 BigDecimal avgDaysToPay,
                                 long all,
                                 long draft,
                                 long open,
                                 long partiallyPaid,
                                 long paid,
                                 long overdue) {
    }
}
