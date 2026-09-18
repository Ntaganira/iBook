/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BillService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendor bill domain service with double-entry ledger posting
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BillForm;
import com.ntaganira.heritier.ibook.dto.BillPaymentForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.MovementType;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillService {

    private static final String MODULE = "bills";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String AP_ACCOUNT_CODE = "2001";
    private static final String VAT_INPUT_ACCOUNT_CODE = "1402";
    private static final String VAT_FALLBACK_ACCOUNT_CODE = "2101";
    private static final String DEFAULT_EXPENSE_CODE = "5000";

    private static final List<DocumentStatus> UNPAID_STATUSES =
            List.of(DocumentStatus.OPEN, DocumentStatus.PARTIALLY_PAID, DocumentStatus.OVERDUE);

    private final BillRepository billRepository;
    private final BillPaymentRepository paymentRepository;
    private final VendorRepository vendorRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final TaxRateRepository taxRateRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditService auditService;

    public BillService(BillRepository billRepository,
                       BillPaymentRepository paymentRepository,
                       VendorRepository vendorRepository,
                       AccountRepository accountRepository,
                       JournalEntryRepository journalEntryRepository,
                       NumberingSequenceRepository numberingSequenceRepository,
                       CompanyRepository companyRepository,
                       TaxRateRepository taxRateRepository,
                       ProductRepository productRepository,
                       StockMovementRepository stockMovementRepository,
                       AuditService auditService) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.vendorRepository = vendorRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.taxRateRepository = taxRateRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Bill> listBills(String q, String status, Long vendorId,
                                LocalDate from, LocalDate to, Pageable pageable) {
        return billRepository.search(trimToNull(q), parseStatus(status), vendorId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Bill getBill(Long id) {
        return billRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BillPayment> paymentsFor(Long billId) {
        return paymentRepository.findByBillIdOrderByPaymentDateAsc(billId);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(Bill bill) {
        if (bill == null || bill.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(bill.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public BillSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new BillSummary(
                zero(billRepository.totalOutstanding()),
                zero(billRepository.totalBilledBetween(monthStart, monthEnd)),
                zero(billRepository.totalOverdue(today)),
                zero(paymentRepository.paidBetween(monthStart, monthEnd)),
                billRepository.count(),
                billRepository.countByStatus(DocumentStatus.DRAFT),
                billRepository.countByStatus(DocumentStatus.OPEN),
                billRepository.countByStatus(DocumentStatus.PARTIALLY_PAID),
                billRepository.countByStatus(DocumentStatus.PAID),
                billRepository.countOverdue(UNPAID_STATUSES, today));
    }

    // ----- Payables aging -----

    /**
     * Payables aging as of a date, grouped by vendor. Buckets count days past each
     * open bill's due date; anything not yet due sits in "current".
     */
    @Transactional(readOnly = true)
    public AgingReport aging(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        Map<Long, List<Bill>> byVendor = new LinkedHashMap<>();
        for (Bill bill : billRepository.findAll()) {
            if (!UNPAID_STATUSES.contains(bill.getStatus()) || bill.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (bill.getBillDate() != null && bill.getBillDate().isAfter(date)) {
                continue;
            }
            byVendor.computeIfAbsent(bill.getVendorId(), k -> new ArrayList<>()).add(bill);
        }

        List<AgingRow> rows = new ArrayList<>();
        BigDecimal tCurrent = BigDecimal.ZERO;
        BigDecimal t1 = BigDecimal.ZERO;
        BigDecimal t2 = BigDecimal.ZERO;
        BigDecimal t3 = BigDecimal.ZERO;
        BigDecimal t4 = BigDecimal.ZERO;

        for (Map.Entry<Long, List<Bill>> entry : byVendor.entrySet()) {
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal b1 = BigDecimal.ZERO;
            BigDecimal b2 = BigDecimal.ZERO;
            BigDecimal b3 = BigDecimal.ZERO;
            BigDecimal b4 = BigDecimal.ZERO;
            long oldest = 0;
            String name = null;
            for (Bill bill : entry.getValue()) {
                name = bill.getVendorName();
                BigDecimal due = bill.getBalanceDue();
                long days = daysPastDue(bill.getDueDate(), date);
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
        return new AgingReport(date, rows,
                new AgingTotals(tCurrent, t1, t2, t3, t4,
                        tCurrent.add(t1).add(t2).add(t3).add(t4)));
    }

    private static long daysPastDue(LocalDate dueDate, LocalDate asOf) {
        if (dueDate == null || !dueDate.isBefore(asOf)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dueDate, asOf);
    }

    /** Open bills for one vendor, oldest first — the drill-down under an aging row. */
    @Transactional(readOnly = true)
    public List<Bill> openBillsFor(Long vendorId, LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<Bill> open = new ArrayList<>();
        for (Bill bill : billRepository.findByVendor(vendorId)) {
            if (!UNPAID_STATUSES.contains(bill.getStatus()) || bill.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (bill.getBillDate() != null && bill.getBillDate().isAfter(date)) {
                continue;
            }
            open.add(bill);
        }
        open.sort((a, b) -> {
            LocalDate x = a.getDueDate() == null ? a.getBillDate() : a.getDueDate();
            LocalDate y = b.getDueDate() == null ? b.getBillDate() : b.getDueDate();
            if (x == null || y == null) {
                return 0;
            }
            return x.compareTo(y);
        });
        return open;
    }

    public record AgingRow(Long vendorId, String vendorName,
                           BigDecimal current, BigDecimal d1to30, BigDecimal d31to60,
                           BigDecimal d61to90, BigDecimal d90plus, BigDecimal total,
                           long billCount, long oldestDays) {}

    public record AgingTotals(BigDecimal current, BigDecimal d1to30, BigDecimal d31to60,
                              BigDecimal d61to90, BigDecimal d90plus, BigDecimal total) {}

    public record AgingReport(LocalDate asOf, List<AgingRow> rows, AgingTotals totals) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    // ----- Create / update -----

    @Transactional
    public Bill saveBill(BillForm form, Long id, String username) {
        Bill bill;
        if (id == null) {
            bill = new Bill();
            bill.setBillNo(nextBillNo());
            bill.setCreatedBy(username);
            bill.setStatus(DocumentStatus.DRAFT);
        } else {
            bill = billRepository.findById(id).orElseThrow();
            if (!bill.isEditable()) {
                throw new IllegalStateException("Only draft bills can be edited");
            }
            bill.getLines().clear();
        }

        Vendor vendor = vendorRepository.findById(form.getVendorId()).orElseThrow();
        bill.setVendorId(vendor.getId());
        bill.setVendorName(vendor.getName());
        bill.setVendorEmail(vendor.getEmail());
        bill.setVendorInvoiceNo(trimToNull(form.getVendorInvoiceNo()));
        bill.setBillDate(form.getBillDate() == null ? LocalDate.now() : form.getBillDate());
        bill.setDueDate(form.getDueDate() == null ? bill.getBillDate().plusDays(30) : form.getDueDate());
        bill.setPaymentTerms(trimToNull(form.getPaymentTerms()));
        bill.setReference(trimToNull(form.getReference()));
        bill.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        bill.setMemo(trimToNull(form.getMemo()));
        bill.setNotes(trimToNull(form.getNotes()));

        Account fallbackExpense = accountRepository.findByCodeIgnoreCase(DEFAULT_EXPENSE_CODE).orElse(null);
        int order = 0;
        for (BillForm.Line lineForm : form.filledLines()) {
            Account expense = lineForm.getExpenseAccountId() == null
                    ? fallbackExpense
                    : accountRepository.findById(lineForm.getExpenseAccountId()).orElse(fallbackExpense);
            ResolvedTax resolved = resolveTax(lineForm.getTaxRateId(), lineForm.taxRateValue());
            Product linked = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);
            BillLine line = BillLine.builder()
                    .description(lineForm.getDescription().trim())
                    .quantity(lineForm.quantityValue())
                    .unitPrice(lineForm.unitPriceValue())
                    .taxRate(resolved.rate())
                    .taxRateId(resolved.rateId())
                    .productId(linked == null ? null : linked.getId())
                    .productSku(linked == null ? null : linked.getSku())
                    .taxTreatment(resolved.treatment())
                    .lineSubtotal(lineForm.lineSubtotal())
                    .lineTax(taxOf(lineForm.lineSubtotal(), resolved.rate()))
                    .lineTotal(lineForm.lineSubtotal().add(taxOf(lineForm.lineSubtotal(), resolved.rate())))
                    .expenseAccountId(expense == null ? null : expense.getId())
                    .expenseAccountCode(expense == null ? null : expense.getCode())
                    .expenseAccountName(expense == null ? null : expense.getName())
                    .sortOrder(order++)
                    .build();
            bill.addLine(line);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (var l : bill.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (var l : bill.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            if (base.signum() == 0) {
                continue;
            }
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            taxTotal = taxTotal.add(taxOf(base.subtract(share), zero(l.getTaxRate())));
        }
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(discount);
        bill.setTaxAmount(taxTotal);
        bill.setTotal(subtotal.subtract(discount).add(taxTotal));

        Bill saved = billRepository.save(bill);
        auditService.log(MODULE, id == null ? "CREATE_BILL" : "UPDATE_BILL",
                "bill#" + saved.getId(), saved.getBillNo() + " \u2014 " + saved.getVendorName());

        if (form.isPostNow()) {
            saved = postBill(saved.getId());
        }
        return saved;
    }

    // ----- Posting to the general ledger -----

    @Transactional
    public Bill postBill(Long id) {
        Bill bill = billRepository.findById(id).orElseThrow();
        if (bill.isPosted()) {
            return bill;
        }
        if (bill.getStatus() == DocumentStatus.VOID) {
            throw new IllegalStateException("A void bill cannot be posted");
        }
        if (bill.getLines().isEmpty()) {
            throw new IllegalStateException("A bill with no line items cannot be posted");
        }

        Account payable = requireAccount(AP_ACCOUNT_CODE, "Accounts payable");
        Account vatInput = accountRepository.findByCodeIgnoreCase(VAT_INPUT_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(VAT_FALLBACK_ACCOUNT_CODE))
                .orElse(null);

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(bill.getBillDate())
                .type(JournalEntryType.BILL)
                .status(JournalEntryStatus.POSTED)
                .reference(bill.getBillNo())
                .memo("Vendor bill " + bill.getBillNo() + " \u2014 " + bill.getVendorName())
                .createdBy(AuditService.currentUsername())
                .build();

        BigDecimal subtotal = zero(bill.getSubtotal());
        BigDecimal discount = zero(bill.getDiscountAmount());
        Map<Long, BigDecimal> expenseByAccount = new LinkedHashMap<>();
        Map<Long, Account> accountCache = new LinkedHashMap<>();

        Account inventoryAccount = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        for (BillLine line : bill.getLines()) {
            Product stocked = line.getProductId() == null ? null
                    : productRepository.findById(line.getProductId()).orElse(null);
            boolean capitalise = stocked != null && stocked.isTrackStock() && inventoryAccount != null;
            Long targetAccountId = capitalise ? inventoryAccount.getId() : line.getExpenseAccountId();
            if (targetAccountId == null) {
                continue;
            }
            BigDecimal base = zero(line.getLineSubtotal());
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            expenseByAccount.merge(targetAccountId, base.subtract(share), BigDecimal::add);
            accountCache.computeIfAbsent(targetAccountId,
                    key -> accountRepository.findById(key).orElse(null));
        }

        int order = 0;
        BigDecimal debitedExpense = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> expense : expenseByAccount.entrySet()) {
            Account account = accountCache.get(expense.getKey());
            if (account == null || expense.getValue().signum() == 0) {
                continue;
            }
            debitedExpense = debitedExpense.add(expense.getValue());
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(bill.getBillNo())
                    .debit(expense.getValue())
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal tax = zero(bill.getTaxAmount());
        if (tax.signum() != 0 && vatInput != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(vatInput.getId())
                    .accountCode(vatInput.getCode())
                    .accountName(vatInput.getName())
                    .memo("Input VAT on " + bill.getBillNo())
                    .debit(tax)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal rounding = bill.getTotal().subtract(debitedExpense).subtract(tax);
        if (rounding.signum() != 0 && !entry.getLines().isEmpty()) {
            JournalLine last = entry.getLines().get(entry.getLines().size() - 1);
            last.setDebit(last.getDebitValue().add(rounding));
        }

        entry.addLine(JournalLine.builder()
                .accountId(payable.getId())
                .accountCode(payable.getCode())
                .accountName(payable.getName())
                .memo(bill.getVendorName())
                .debit(BigDecimal.ZERO)
                .credit(bill.getTotal())
                .sortOrder(order)
                .build());

        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        recordStockMovements(bill, savedEntry.getId());

        bill.setJournalEntryId(savedEntry.getId());
        bill.setStatus(zero(bill.getAmountPaid()).signum() > 0
                ? resolvePaidStatus(bill) : DocumentStatus.OPEN);
        Bill saved = billRepository.save(bill);

        auditService.log(MODULE, "POST_BILL", "bill#" + saved.getId(),
                saved.getBillNo() + " posted as " + savedEntry.getEntryNo());
        return saved;
    }

    // ----- Payments -----

    @Transactional
    public BillPayment recordPayment(Long billId, BillPaymentForm form) {
        Bill bill = billRepository.findById(billId).orElseThrow();
        if (bill.getStatus() == DocumentStatus.VOID) {
            throw new IllegalStateException("A void bill cannot be paid");
        }
        if (!bill.isPosted()) {
            throw new IllegalStateException("Post the bill before recording a payment");
        }
        BigDecimal amount = form.amount() == null ? BigDecimal.ZERO : form.amount();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(bill.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Payment exceeds the outstanding balance");
        }

        Account source = accountRepository.findById(form.paidFromAccountId()).orElseThrow();
        Account payable = requireAccount(AP_ACCOUNT_CODE, "Accounts payable");
        LocalDate paymentDate = form.paymentDate() == null ? LocalDate.now() : form.paymentDate();

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(paymentDate)
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(bill.getBillNo())
                .memo("Payment made for " + bill.getBillNo() + " \u2014 " + bill.getVendorName())
                .createdBy(AuditService.currentUsername())
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(payable.getId())
                .accountCode(payable.getCode())
                .accountName(payable.getName())
                .memo(bill.getVendorName())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(source.getId())
                .accountCode(source.getCode())
                .accountName(source.getName())
                .memo(bill.getBillNo())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        BillPayment payment = BillPayment.builder()
                .paymentDate(paymentDate)
                .amount(amount)
                .method(parseMethod(form.method()))
                .reference(trimToNull(form.reference()))
                .paidFromAccountId(source.getId())
                .paidFromAccountCode(source.getCode())
                .paidFromAccountName(source.getName())
                .journalEntryId(savedEntry.getId())
                .notes(trimToNull(form.notes()))
                .createdBy(AuditService.currentUsername())
                .build();
        payment.setBill(bill);
        BillPayment savedPayment = paymentRepository.save(payment);

        bill.setAmountPaid(zero(bill.getAmountPaid()).add(amount));
        bill.setStatus(resolvePaidStatus(bill));
        billRepository.save(bill);

        auditService.log(MODULE, "PAY_BILL", "bill#" + billId,
                bill.getBillNo() + " payment " + amount.toPlainString());
        return savedPayment;
    }

    private DocumentStatus resolvePaidStatus(Bill bill) {
        return bill.getBalanceDue().signum() == 0
                ? DocumentStatus.PAID
                : DocumentStatus.PARTIALLY_PAID;
    }

    // ----- Payments made -----

    /** Payments in a period, newest first, optionally narrowed to one method. */
    @Transactional(readOnly = true)
    public PaymentsReport paymentsMade(LocalDate from, LocalDate to, String method) {
        PaymentMethod filter = parseMethodOrNull(method);
        List<PaymentRow> rows = new ArrayList<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> byAccount = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (BillPayment payment : paymentRepository.findBetween(from, to)) {
            if (filter != null && payment.getMethod() != filter) {
                continue;
            }
            Bill bill = payment.getBill();
            BigDecimal amount = zero(payment.getAmount());
            rows.add(new PaymentRow(
                    payment.getId(),
                    payment.getPaymentDate(),
                    amount,
                    payment.getMethod() == null ? null : payment.getMethod().name(),
                    payment.getReference(),
                    payment.getPaidFromAccountCode(),
                    payment.getPaidFromAccountName(),
                    payment.getJournalEntryId(),
                    bill == null ? null : bill.getId(),
                    bill == null ? null : bill.getBillNo(),
                    bill == null ? null : bill.getVendorId(),
                    bill == null ? null : bill.getVendorName(),
                    bill == null ? null : bill.getCurrencyCode()));
            total = total.add(amount);
            byMethod.merge(payment.getMethod() == null ? "UNKNOWN" : payment.getMethod().name(),
                    amount, BigDecimal::add);
            byAccount.merge(payment.getPaidFromAccountCode() == null
                            ? "-" : payment.getPaidFromAccountCode() + " - " + payment.getPaidFromAccountName(),
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

    // ----- Vendor statement -----

    /**
     * Statement of account for one party: balance brought forward at the period start,
     * then every charge and payment in date order with a running balance.
     */
    @Transactional(readOnly = true)
    public Statement statementFor(Long partyId, LocalDate from, LocalDate to) {
        List<Bill> docs = billRepository.findByVendor(partyId);
        List<BillPayment> payments = paymentRepository.findByVendor(partyId);

        BigDecimal opening = BigDecimal.ZERO;
        String name = null;
        String currency = null;

        for (Bill d : docs) {
            if (d.getStatus() == DocumentStatus.DRAFT || d.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            name = d.getVendorName();
            if (currency == null) {
                currency = d.getCurrencyCode();
            }
            if (d.getBillDate() != null && d.getBillDate().isBefore(from)) {
                opening = opening.add(zero(d.getTotal()));
            }
        }
        for (BillPayment p : payments) {
            if (p.getPaymentDate() != null && p.getPaymentDate().isBefore(from)) {
                opening = opening.subtract(zero(p.getAmount()));
            }
        }

        List<StatementLine> lines = new ArrayList<>();
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;

        for (Bill d : docs) {
            if (d.getStatus() == DocumentStatus.DRAFT || d.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            LocalDate date = d.getBillDate();
            if (date == null || date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            charges = charges.add(zero(d.getTotal()));
            lines.add(new StatementLine(date, "BILL", d.getId(), d.getBillNo(),
                    d.getReference(), zero(d.getTotal()), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (BillPayment p : payments) {
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

    // ----- Lifecycle -----

    @Transactional
    public Bill voidBill(Long id, String reason) {
        Bill bill = billRepository.findById(id).orElseThrow();
        if (bill.getStatus() == DocumentStatus.VOID) {
            return bill;
        }
        if (zero(bill.getAmountPaid()).signum() > 0) {
            throw new IllegalStateException("A bill with recorded payments cannot be voided");
        }
        if (bill.isPosted()) {
            JournalEntry original = journalEntryRepository.findById(bill.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(bill.getBillNo())
                        .memo("Reversal of " + original.getEntryNo() + " \u2014 voided bill " + bill.getBillNo())
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
        bill.setStatus(DocumentStatus.VOID);
        Bill saved = billRepository.save(bill);
        auditService.log(MODULE, "VOID_BILL", "bill#" + id,
                saved.getBillNo() + (reason == null || reason.isBlank() ? "" : " \u2014 " + reason.trim()));
        return saved;
    }

    @Transactional
    public void deleteDraft(Long id) {
        Bill bill = billRepository.findById(id).orElse(null);
        if (bill == null) {
            return;
        }
        if (!bill.isEditable()) {
            throw new IllegalStateException("Only draft bills can be deleted");
        }
        billRepository.delete(bill);
        auditService.log(MODULE, "DELETE_BILL", "bill#" + id, bill.getBillNo() + " draft deleted");
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("BILL").orElse(null);
        return seq == null ? "BILL-0001" : seq.previewNext();
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private String nextBillNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("BILL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "BILL-" : seq.getPrefix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next);
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "BILL-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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
                        "Account " + code + " (" + label + ") is missing from the chart of accounts"));
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
            return DocumentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return PaymentMethod.BANK_TRANSFER;
        }
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
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

    /** Stocked lines enter inventory when the bill is posted. */
    private void recordStockMovements(Bill bill, Long journalEntryId) {
        for (BillLine line : bill.getLines()) {
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
                    .movementType(MovementType.PURCHASE)
                    .quantity(qty)
                    .unitCost(zero(line.getUnitPrice()))
                    .movementDate(bill.getBillDate())
                    .reference(bill.getBillNo())
                    .notes(bill.getVendorName())
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

    public record BillSummary(BigDecimal outstanding,
                              BigDecimal billedThisMonth,
                              BigDecimal overdueAmount,
                              BigDecimal paidThisMonth,
                              long all,
                              long draft,
                              long open,
                              long partiallyPaid,
                              long paid,
                              long overdue) {
    }
}
