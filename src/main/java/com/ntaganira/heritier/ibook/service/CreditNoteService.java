/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : CreditNoteService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer credit notes, posting the reverse of a sales invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.CreditNoteForm;
import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.CreditNoteStatus;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
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
public class CreditNoteService {

    private static final String MODULE = "credit-notes";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String AR_ACCOUNT_CODE = "1201";
    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String COGS_ACCOUNT_CODE = "5200";
    private static final String COGS_FALLBACK_CODE = "5000";
    private static final String VAT_ACCOUNT_CODE = "2101";
    private static final String DEFAULT_REVENUE_CODE = "4002";

    private static final List<DocumentStatus> CREDITABLE_STATUSES =
            List.of(DocumentStatus.OPEN, DocumentStatus.PARTIALLY_PAID, DocumentStatus.OVERDUE);

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final TaxRateRepository taxRateRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditService auditService;

    public CreditNoteService(CreditNoteRepository creditNoteRepository,
                             InvoiceRepository invoiceRepository,
                             CustomerRepository customerRepository,
                             AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             CompanyRepository companyRepository,
                             TaxRateRepository taxRateRepository,
                             ProductRepository productRepository,
                             StockMovementRepository stockMovementRepository,
                             AuditService auditService) {
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
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
    public Page<CreditNote> list(String q, String status, Long customerId,
                                 LocalDate from, LocalDate to, Pageable pageable) {
        return creditNoteRepository.search(trimToNull(q), parseStatus(status), customerId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public CreditNote get(Long id) {
        return id == null ? null : creditNoteRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(CreditNote note) {
        if (note == null || note.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(note.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Invoice linkedInvoice(CreditNote note) {
        if (note == null || note.getInvoiceId() == null) {
            return null;
        }
        return invoiceRepository.findById(note.getInvoiceId()).orElse(null);
    }

    /** Posted invoices for a customer that still carry a balance a credit note could settle. */
    @Transactional(readOnly = true)
    public List<Invoice> creditableInvoices(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        List<Invoice> open = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByCustomer(customerId)) {
            if (invoice.isPosted() && CREDITABLE_STATUSES.contains(invoice.getStatus())
                    && invoice.getBalanceDue().signum() > 0) {
                open.add(invoice);
            }
        }
        return open;
    }

    /** Every still-owing posted invoice, so the form can offer one to credit. */
    @Transactional(readOnly = true)
    public List<Invoice> creditableInvoices() {
        List<Invoice> open = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (invoice.isPosted() && CREDITABLE_STATUSES.contains(invoice.getStatus())
                    && invoice.getBalanceDue().signum() > 0) {
                open.add(invoice);
            }
        }
        open.sort((a, b) -> b.getIssueDate().compareTo(a.getIssueDate()));
        return open;
    }

    @Transactional(readOnly = true)
    public CreditNoteSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new CreditNoteSummary(
                creditNoteRepository.count(),
                creditNoteRepository.countByStatus(CreditNoteStatus.DRAFT),
                creditNoteRepository.countByStatus(CreditNoteStatus.ISSUED),
                creditNoteRepository.countByStatus(CreditNoteStatus.APPLIED),
                creditNoteRepository.countByStatus(CreditNoteStatus.VOID),
                zero(creditNoteRepository.totalFor(
                        List.of(CreditNoteStatus.ISSUED, CreditNoteStatus.APPLIED))),
                zero(creditNoteRepository.totalCreditedBetween(monthStart, monthEnd)),
                zero(creditNoteRepository.totalUnapplied()));
    }

    // ----- Create / update -----

    @Transactional
    public CreditNote save(CreditNoteForm form, Long id, String username) {
        CreditNote note;
        if (id == null) {
            note = new CreditNote();
            note.setCreditNoteNo(nextCreditNoteNo());
            note.setCreatedBy(username);
            note.setStatus(CreditNoteStatus.DRAFT);
        } else {
            note = creditNoteRepository.findById(id).orElseThrow();
            if (!note.isEditable()) {
                throw new IllegalStateException("Only draft credit notes can be edited");
            }
            note.getLines().clear();
        }

        Customer customer = customerRepository.findById(form.getCustomerId()).orElseThrow();
        note.setCustomerId(customer.getId());
        note.setCustomerName(customer.getName());
        note.setCustomerEmail(customer.getEmail());
        note.setCreditDate(form.getCreditDate() == null ? LocalDate.now() : form.getCreditDate());
        note.setReference(trimToNull(form.getReference()));
        note.setReason(trimToNull(form.getReason()));
        note.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        note.setCustomerMessage(trimToNull(form.getCustomerMessage()));
        note.setNotes(trimToNull(form.getNotes()));
        note.setRestockItems(form.isRestockItems());

        Invoice linked = form.getInvoiceId() == null ? null
                : invoiceRepository.findById(form.getInvoiceId()).orElse(null);
        if (linked != null && !linked.getCustomerId().equals(customer.getId())) {
            throw new IllegalStateException("The selected invoice belongs to a different customer");
        }
        note.setInvoiceId(linked == null ? null : linked.getId());
        note.setInvoiceNo(linked == null ? null : linked.getInvoiceNo());

        Account fallbackRevenue = accountRepository.findByCodeIgnoreCase(DEFAULT_REVENUE_CODE).orElse(null);
        int order = 0;
        for (InvoiceForm.Line lineForm : form.filledLines()) {
            Account revenue = lineForm.getRevenueAccountId() == null
                    ? fallbackRevenue
                    : accountRepository.findById(lineForm.getRevenueAccountId()).orElse(fallbackRevenue);
            ResolvedTax resolved = resolveTax(lineForm.getTaxRateId(), lineForm.taxRateValue());
            Product product = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);
            BigDecimal base = lineForm.lineSubtotal();
            BigDecimal tax = taxOf(base, resolved.rate());
            note.addLine(CreditNoteLine.builder()
                    .description(lineForm.getDescription().trim())
                    .productId(product == null ? null : product.getId())
                    .productSku(product == null ? null : product.getSku())
                    .quantity(lineForm.quantityValue())
                    .unitPrice(lineForm.unitPriceValue())
                    .taxRate(resolved.rate())
                    .taxRateId(resolved.rateId())
                    .taxTreatment(resolved.treatment())
                    .lineSubtotal(base)
                    .lineTax(tax)
                    .lineTotal(base.add(tax))
                    .revenueAccountId(revenue == null ? null : revenue.getId())
                    .revenueAccountCode(revenue == null ? null : revenue.getCode())
                    .revenueAccountName(revenue == null ? null : revenue.getName())
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreditNoteLine l : note.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (CreditNoteLine l : note.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            if (base.signum() == 0) {
                continue;
            }
            taxTotal = taxTotal.add(taxOf(base.subtract(discountShare(base, discount, subtotal)),
                    zero(l.getTaxRate())));
        }
        note.setSubtotal(subtotal);
        note.setDiscountAmount(discount);
        note.setTaxAmount(taxTotal);
        note.setTotal(subtotal.subtract(discount).add(taxTotal));

        CreditNote saved = creditNoteRepository.save(note);
        auditService.log(MODULE, id == null ? "CREATE_CREDIT_NOTE" : "UPDATE_CREDIT_NOTE",
                "creditNote#" + saved.getId(), saved.getCreditNoteNo() + " — " + saved.getCustomerName());

        if (form.isPostNow()) {
            saved = post(saved.getId());
        }
        return saved;
    }

    // ----- Posting to the general ledger -----

    /**
     * Posts the mirror image of an invoice: receivables are credited, revenue and output VAT
     * are debited back, and returned goods move cost out of cost of sales into inventory.
     */
    @Transactional
    public CreditNote post(Long id) {
        CreditNote note = creditNoteRepository.findById(id).orElseThrow();
        if (note.isPosted()) {
            return note;
        }
        if (note.getStatus() == CreditNoteStatus.VOID) {
            throw new IllegalStateException("A void credit note cannot be posted");
        }
        if (note.getLines().isEmpty()) {
            throw new IllegalStateException("A credit note with no line items cannot be posted");
        }

        Account receivable = requireAccount(AR_ACCOUNT_CODE, "Accounts receivable");
        Account vatPayable = accountRepository.findByCodeIgnoreCase(VAT_ACCOUNT_CODE).orElse(null);

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(note.getCreditDate())
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(note.getCreditNoteNo())
                .memo("Credit note " + note.getCreditNoteNo() + " — " + note.getCustomerName())
                .createdBy(AuditService.currentUsername())
                .build();

        int order = 0;
        BigDecimal subtotal = zero(note.getSubtotal());
        BigDecimal discount = zero(note.getDiscountAmount());
        Map<Long, BigDecimal> revenueByAccount = new LinkedHashMap<>();
        Map<Long, Account> accountCache = new LinkedHashMap<>();

        for (CreditNoteLine line : note.getLines()) {
            if (line.getRevenueAccountId() == null) {
                continue;
            }
            BigDecimal net = zero(line.getLineSubtotal())
                    .subtract(discountShare(zero(line.getLineSubtotal()), discount, subtotal));
            revenueByAccount.merge(line.getRevenueAccountId(), net, BigDecimal::add);
            accountCache.computeIfAbsent(line.getRevenueAccountId(),
                    key -> accountRepository.findById(key).orElse(null));
        }

        BigDecimal debitedRevenue = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> revenue : revenueByAccount.entrySet()) {
            Account account = accountCache.get(revenue.getKey());
            if (account == null || revenue.getValue().signum() == 0) {
                continue;
            }
            debitedRevenue = debitedRevenue.add(revenue.getValue());
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(note.getCreditNoteNo())
                    .debit(revenue.getValue())
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal tax = zero(note.getTaxAmount());
        if (tax.signum() != 0 && vatPayable != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(vatPayable.getId())
                    .accountCode(vatPayable.getCode())
                    .accountName(vatPayable.getName())
                    .memo("VAT on " + note.getCreditNoteNo())
                    .debit(tax)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        // Rounding drift from the per-line discount split lands on the last debit line.
        BigDecimal rounding = zero(note.getTotal()).subtract(debitedRevenue).subtract(tax);
        if (rounding.signum() != 0 && !entry.getLines().isEmpty()) {
            JournalLine last = entry.getLines().get(entry.getLines().size() - 1);
            last.setDebit(last.getDebitValue().add(rounding));
        }

        entry.addLine(JournalLine.builder()
                .accountId(receivable.getId())
                .accountCode(receivable.getCode())
                .accountName(receivable.getName())
                .memo(note.getCustomerName())
                .debit(BigDecimal.ZERO)
                .credit(zero(note.getTotal()))
                .sortOrder(order++)
                .build());

        BigDecimal returnedCost = returnedCost(note);
        Account inventoryAccount = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        Account cogsAccount = accountRepository.findByCodeIgnoreCase(COGS_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(COGS_FALLBACK_CODE)).orElse(null);
        if (returnedCost.signum() != 0 && inventoryAccount != null && cogsAccount != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(inventoryAccount.getId())
                    .accountCode(inventoryAccount.getCode())
                    .accountName(inventoryAccount.getName())
                    .memo("Stock returned " + note.getCreditNoteNo())
                    .debit(returnedCost)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
            entry.addLine(JournalLine.builder()
                    .accountId(cogsAccount.getId())
                    .accountCode(cogsAccount.getCode())
                    .accountName(cogsAccount.getName())
                    .memo("Cost of sales reversed " + note.getCreditNoteNo())
                    .debit(BigDecimal.ZERO)
                    .credit(returnedCost)
                    .sortOrder(order)
                    .build());
        }

        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        recordStockMovements(note, savedEntry.getId());

        note.setJournalEntryId(savedEntry.getId());
        BigDecimal applied = applyToInvoice(note);
        note.setAppliedAmount(applied);
        note.setStatus(applied.compareTo(zero(note.getTotal())) >= 0 && applied.signum() > 0
                ? CreditNoteStatus.APPLIED : CreditNoteStatus.ISSUED);
        CreditNote saved = creditNoteRepository.save(note);

        auditService.log(MODULE, "POST_CREDIT_NOTE", "creditNote#" + saved.getId(),
                saved.getCreditNoteNo() + " posted as " + savedEntry.getEntryNo());
        return saved;
    }

    /**
     * Sets the credit against the linked invoice, capped at what that invoice still owes.
     * Anything left over stays on the note as an unapplied customer credit.
     */
    private BigDecimal applyToInvoice(CreditNote note) {
        if (note.getInvoiceId() == null) {
            return BigDecimal.ZERO;
        }
        Invoice invoice = invoiceRepository.findById(note.getInvoiceId()).orElse(null);
        if (invoice == null || invoice.getStatus() == DocumentStatus.VOID || !invoice.isPosted()) {
            return BigDecimal.ZERO;
        }
        BigDecimal outstanding = invoice.getBalanceDue();
        if (outstanding.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal applied = zero(note.getTotal()).min(outstanding);
        invoice.setCreditedAmount(zero(invoice.getCreditedAmount()).add(applied));
        invoice.setStatus(invoice.getSettlementStatus());
        invoiceRepository.save(invoice);
        return applied;
    }

    // ----- Lifecycle -----

    @Transactional
    public CreditNote voidCreditNote(Long id, String reason) {
        CreditNote note = creditNoteRepository.findById(id).orElseThrow();
        if (note.getStatus() == CreditNoteStatus.VOID) {
            return note;
        }
        if (note.isPosted()) {
            JournalEntry original = journalEntryRepository.findById(note.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(note.getCreditNoteNo())
                        .memo("Reversal of " + original.getEntryNo() + " — voided credit note "
                                + note.getCreditNoteNo())
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
        unapplyFromInvoice(note);
        note.setAppliedAmount(BigDecimal.ZERO);
        note.setStatus(CreditNoteStatus.VOID);
        CreditNote saved = creditNoteRepository.save(note);
        auditService.log(MODULE, "VOID_CREDIT_NOTE", "creditNote#" + id,
                saved.getCreditNoteNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    private void unapplyFromInvoice(CreditNote note) {
        if (note.getInvoiceId() == null || zero(note.getAppliedAmount()).signum() == 0) {
            return;
        }
        Invoice invoice = invoiceRepository.findById(note.getInvoiceId()).orElse(null);
        if (invoice == null) {
            return;
        }
        BigDecimal credited = zero(invoice.getCreditedAmount()).subtract(zero(note.getAppliedAmount()));
        invoice.setCreditedAmount(credited.signum() < 0 ? BigDecimal.ZERO : credited);
        if (invoice.getStatus() != DocumentStatus.VOID && invoice.getStatus() != DocumentStatus.DRAFT) {
            invoice.setStatus(invoice.getSettlementStatus());
        }
        invoiceRepository.save(invoice);
    }

    @Transactional
    public void delete(Long id) {
        CreditNote note = creditNoteRepository.findById(id).orElse(null);
        if (note == null) {
            return;
        }
        if (!note.isEditable()) {
            throw new IllegalStateException("Only draft credit notes can be deleted");
        }
        creditNoteRepository.delete(note);
        auditService.log(MODULE, "DELETE_CREDIT_NOTE", "creditNote#" + id,
                note.getCreditNoteNo() + " draft deleted");
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("CREDIT_NOTE").orElse(null);
        return seq == null ? "CN-0001" : seq.previewNext();
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    private String nextCreditNoteNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("CREDIT_NOTE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "CN-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "CN-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    /** Cost of stocked goods coming back, valued the same way the sale took them out. */
    private BigDecimal returnedCost(CreditNote note) {
        if (!note.isRestockItems()) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = BigDecimal.ZERO;
        for (CreditNoteLine line : note.getLines()) {
            Product product = line.getProductId() == null ? null
                    : productRepository.findById(line.getProductId()).orElse(null);
            if (product == null || !product.isTrackStock()) {
                continue;
            }
            cost = cost.add(zero(line.getQuantity()).multiply(zero(product.getCostPrice())));
        }
        return cost;
    }

    private void recordStockMovements(CreditNote note, Long journalEntryId) {
        if (!note.isRestockItems()) {
            return;
        }
        for (CreditNoteLine line : note.getLines()) {
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
                    .movementType(MovementType.ADJUSTMENT_IN)
                    .quantity(qty)
                    .unitCost(zero(product.getCostPrice()))
                    .movementDate(note.getCreditDate())
                    .reference(note.getCreditNoteNo())
                    .notes(note.getCustomerName())
                    .journalEntryId(journalEntryId)
                    .createdBy(AuditService.currentUsername())
                    .build());
        }
    }

    private Account requireAccount(String code, String label) {
        return accountRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Chart of accounts is missing " + label + " (" + code + ")"));
    }

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

    private record ResolvedTax(Long rateId, BigDecimal rate, TaxTreatment treatment) {}

    private static BigDecimal discountShare(BigDecimal base, BigDecimal discount, BigDecimal subtotal) {
        if (discount.signum() == 0 || subtotal.signum() == 0 || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
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

    private static CreditNoteStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CreditNoteStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record CreditNoteSummary(long all, long draft, long issued, long applied, long voided,
                                    BigDecimal totalCredited, BigDecimal creditedThisMonth,
                                    BigDecimal unapplied) {}
}
