/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : DocumentCaptureService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Turning a scanned document into a draft expense
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.DocumentExtractionForm;
import com.ntaganira.heritier.ibook.dto.ExpenseForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Attachment;
import com.ntaganira.heritier.ibook.entity.DocumentExtraction;
import com.ntaganira.heritier.ibook.entity.Expense;
import com.ntaganira.heritier.ibook.entity.Vendor;
import com.ntaganira.heritier.ibook.enums.DocumentLinkType;
import com.ntaganira.heritier.ibook.enums.ExtractionSource;
import com.ntaganira.heritier.ibook.enums.ExtractionStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.AttachmentRepository;
import com.ntaganira.heritier.ibook.repository.DocumentExtractionRepository;
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

/**
 * Turns a scanned receipt or invoice into a draft expense.
 *
 * <p>This is the half of document capture that decides money, and it works whether or not a
 * text-recognition service exists. There is no engine in this codebase — {@link OcrEngine} is the
 * seam one would plug into and {@link UnavailableOcrEngine} says so plainly — so today the figures
 * are typed in against the document on screen. Everything after that is identical either way: the
 * same checks, the same draft, the same approval.
 *
 * <p>Three rules, all of them about not letting a figure off a photograph reach the books
 * unexamined:
 *
 * <ol>
 *   <li><strong>Converting always produces a draft.</strong> Never a posting. A figure read off a
 *       document is a claim about a piece of paper, not a fact about the books.</li>
 *   <li><strong>The arithmetic is checked but not corrected.</strong> If subtotal plus tax does
 *       not reach the printed total, it is flagged and conversion is refused — because which of
 *       the three figures is wrong is not something this code can know, and silently deriving one
 *       from the other two would bury the misread rather than surface it.</li>
 *   <li><strong>One document converts once.</strong> The draft's number is written back onto the
 *       capture, so the same receipt cannot quietly become two expenses.</li>
 * </ol>
 *
 * <p>The document is attached to the expense it becomes, so the receipt is the evidence for the
 * transaction rather than a file sitting near it.
 */
@Service
public class DocumentCaptureService {

    private static final String MODULE = "documents";

    private final DocumentExtractionRepository documentExtractionRepository;
    private final AttachmentRepository attachmentRepository;
    private final AccountRepository accountRepository;
    private final VendorRepository vendorRepository;
    private final DocumentService documentService;
    private final ExpenseService expenseService;
    private final OcrEngine ocrEngine;
    private final AuditService auditService;

    public DocumentCaptureService(DocumentExtractionRepository documentExtractionRepository,
                                  AttachmentRepository attachmentRepository,
                                  AccountRepository accountRepository,
                                  VendorRepository vendorRepository,
                                  DocumentService documentService,
                                  ExpenseService expenseService,
                                  OcrEngine ocrEngine,
                                  AuditService auditService) {
        this.documentExtractionRepository = documentExtractionRepository;
        this.attachmentRepository = attachmentRepository;
        this.accountRepository = accountRepository;
        this.vendorRepository = vendorRepository;
        this.documentService = documentService;
        this.expenseService = expenseService;
        this.ocrEngine = ocrEngine;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<DocumentExtraction> list(String q, String status, Pageable pageable) {
        return documentExtractionRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public DocumentExtraction get(Long id) {
        return id == null ? null : documentExtractionRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Attachment attachmentFor(DocumentExtraction extraction) {
        if (extraction == null) {
            return null;
        }
        return attachmentRepository.findById(extraction.getAttachmentId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Account> expenseAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            if (account.getType() == com.ntaganira.heritier.ibook.enums.AccountType.EXPENSE) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    @Transactional(readOnly = true)
    public List<Account> paymentAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            String code = account.getCode();
            if (code != null && (code.startsWith("10") || code.startsWith("11"))) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    @Transactional(readOnly = true)
    public List<Vendor> vendors() {
        return vendorRepository.findAll();
    }

    /**
     * Documents in the library that nobody has captured yet, so the inbox can offer them rather
     * than making somebody upload the same receipt twice.
     */
    @Transactional(readOnly = true)
    public List<Attachment> uncaptured() {
        List<Attachment> waiting = new ArrayList<>();
        for (Attachment attachment : attachmentRepository.findAll()) {
            if (attachment.isArchived() || attachment.isTemplate()) {
                continue;
            }
            if (documentExtractionRepository.existsByAttachmentId(attachment.getId())) {
                continue;
            }
            waiting.add(attachment);
        }
        return waiting;
    }

    @Transactional(readOnly = true)
    public CaptureSummary summary() {
        return new CaptureSummary(
                documentExtractionRepository.count(),
                documentExtractionRepository.countByStatus(ExtractionStatus.PENDING),
                documentExtractionRepository.countByStatus(ExtractionStatus.READY),
                documentExtractionRepository.countByStatus(ExtractionStatus.CONVERTED),
                documentExtractionRepository.countByStatus(ExtractionStatus.DISMISSED),
                uncaptured().size(),
                ocrEngine.isAvailable(),
                ocrEngine.describe());
    }

    // ----- Capture -----

    /**
     * Starts a capture against a stored document, asking the engine what it can read first.
     *
     * <p>With no engine configured this simply opens an empty form against the file, which is the
     * honest outcome — the alternative is a page that looks like it tried and failed.
     */
    @Transactional
    public DocumentExtraction capture(Long attachmentId, String username) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("That document could not be found"));
        DocumentExtraction existing = documentExtractionRepository
                .findByAttachmentId(attachmentId).orElse(null);
        if (existing != null) {
            return existing;
        }

        DocumentExtraction extraction = DocumentExtraction.builder()
                .attachmentId(attachment.getId())
                .fileName(attachment.getOriginalName())
                .status(ExtractionStatus.PENDING)
                .source(ExtractionSource.TYPED)
                .currencyCode(expenseService.baseCurrency())
                .capturedBy(username)
                .build();

        if (ocrEngine.isAvailable()) {
            DocumentService.Download download = documentService.download(attachment.getId());
            OcrEngine.Extraction read = ocrEngine.read(download.resource(), attachment.getContentType());
            extraction.setSource(ExtractionSource.ENGINE);
            extraction.setEngineName(ocrEngine.getClass().getSimpleName());
            extraction.setSupplierName(read.supplierName());
            extraction.setDocumentNo(read.documentNo());
            extraction.setDocumentDate(read.documentDate());
            if (read.currencyCode() != null) {
                extraction.setCurrencyCode(read.currencyCode());
            }
            extraction.setSubtotal(zero(read.subtotal()));
            extraction.setTaxAmount(zero(read.taxAmount()));
            extraction.setTotal(zero(read.total()));
        }

        DocumentExtraction saved = documentExtractionRepository.save(extraction);
        auditService.log(MODULE, "CAPTURE_DOCUMENT", "documentExtraction#" + saved.getId(),
                saved.getFileName());
        return saved;
    }

    @Transactional
    public DocumentExtraction save(DocumentExtractionForm form, Long id, String username) {
        DocumentExtraction extraction = documentExtractionRepository.findById(id).orElseThrow();
        if (!extraction.isEditable()) {
            throw new IllegalStateException("This document has already been dealt with");
        }

        Vendor vendor = form.vendorId() == null ? null
                : vendorRepository.findById(form.vendorId()).orElse(null);
        extraction.setVendorId(vendor == null ? null : vendor.getId());
        extraction.setSupplierName(vendor != null ? vendor.getName() : trimToNull(form.supplierName()));
        extraction.setDocumentNo(trimToNull(form.documentNo()));
        extraction.setDocumentDate(form.documentDate());
        extraction.setCurrencyCode(form.currencyCode() == null || form.currencyCode().isBlank()
                ? expenseService.baseCurrency() : form.currencyCode());
        extraction.setSubtotal(round(form.subtotalValue()));
        extraction.setTaxAmount(round(form.taxAmountValue()));
        extraction.setTotal(round(form.totalValue()));
        extraction.setExpenseAccountId(form.expenseAccountId());
        extraction.setPaymentAccountId(form.paymentAccountId());
        extraction.setNotes(trimToNull(form.notes()));

        // A figure typed over what an engine read is a person's figure now, whatever put it there
        // first. Leaving it marked as machine-read would overstate how much of this was automatic.
        extraction.setSource(ExtractionSource.TYPED);
        extraction.setStatus(extraction.isConvertible() && extraction.isArithmeticSound()
                ? ExtractionStatus.READY : ExtractionStatus.PENDING);

        DocumentExtraction saved = documentExtractionRepository.save(extraction);
        auditService.log(MODULE, "UPDATE_CAPTURE", "documentExtraction#" + saved.getId(),
                saved.getFileName());

        if (form.convertNowValue()) {
            saved = convert(saved.getId(), username);
        }
        return saved;
    }

    /** Raises a draft expense from the captured figures and attaches the document to it. */
    @Transactional
    public DocumentExtraction convert(Long id, String username) {
        DocumentExtraction extraction = documentExtractionRepository.findById(id).orElseThrow();
        if (extraction.isConverted()) {
            throw new IllegalStateException("This document has already become "
                    + extraction.getExpenseNo() + ". Raising it again would enter the same spend "
                    + "twice.");
        }
        if (!extraction.isEditable()) {
            throw new IllegalStateException("This document has been dismissed");
        }
        if (!extraction.isArithmeticSound()) {
            throw new IllegalStateException("The figures do not add up — subtotal plus tax comes to "
                    + zero(extraction.getSubtotal()).add(zero(extraction.getTaxAmount())).toPlainString()
                    + " against a printed total of " + zero(extraction.getTotal()).toPlainString()
                    + ". Read the document again; which of the three is wrong is not something "
                    + "this page can tell.");
        }
        if (!extraction.isConvertible()) {
            throw new IllegalStateException("A draft needs a supplier, a date, an expense account, "
                    + "an account it was paid from, and a total above zero");
        }

        ExpenseForm form = new ExpenseForm();
        form.setVendorId(extraction.getVendorId());
        form.setPayeeName(extraction.getSupplierName());
        form.setExpenseDate(extraction.getDocumentDate());
        form.setPaymentAccountId(extraction.getPaymentAccountId());
        form.setPaymentMethod("CASH");
        form.setReference(extraction.getDocumentNo());
        form.setCurrencyCode(extraction.getCurrencyCode());
        form.setMemo("From " + extraction.getFileName());
        form.setNotes(extraction.getNotes());
        // Never posts. The whole point of the review step is that somebody looks at the draft.
        form.setPostNow(false);

        ExpenseForm.Line line = form.getLines().get(0);
        line.setDescription(extraction.getDocumentNo() == null
                ? extraction.getSupplierName()
                : extraction.getSupplierName() + " — " + extraction.getDocumentNo());
        line.setAmount(zero(extraction.getSubtotal()));
        line.setExpenseAccountId(extraction.getExpenseAccountId());
        // The tax is carried as a rate worked back from the amounts on the document rather than
        // from a configured rate, because what matters here is reproducing what the paper says.
        line.setTaxRate(taxRateFrom(extraction.getSubtotal(), extraction.getTaxAmount()));

        Expense expense = expenseService.save(form, null, username);

        extraction.setExpenseId(expense.getId());
        extraction.setExpenseNo(expense.getExpenseNo());
        extraction.setStatus(ExtractionStatus.CONVERTED);
        DocumentExtraction saved = documentExtractionRepository.save(extraction);

        Attachment attachment = attachmentRepository.findById(extraction.getAttachmentId()).orElse(null);
        if (attachment != null) {
            attachment.setLinkType(DocumentLinkType.EXPENSE);
            attachment.setLinkId(expense.getId());
            attachment.setLinkLabel(expense.getExpenseNo() + " — " + expense.getPayeeName());
            attachmentRepository.save(attachment);
        }

        auditService.log(MODULE, "CONVERT_CAPTURE", "documentExtraction#" + saved.getId(),
                saved.getFileName() + " became draft " + expense.getExpenseNo());
        return saved;
    }

    /**
     * The rate the document implies. Derived rather than looked up, because a receipt that charged
     * an unusual rate should reproduce as what it charged, not as what the tax table says today.
     */
    private static BigDecimal taxRateFrom(BigDecimal subtotal, BigDecimal tax) {
        BigDecimal base = zero(subtotal);
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return zero(tax).multiply(new BigDecimal("100")).divide(base, 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public DocumentExtraction dismiss(Long id, String reason) {
        DocumentExtraction extraction = documentExtractionRepository.findById(id).orElseThrow();
        if (extraction.isConverted()) {
            throw new IllegalStateException("This document has already become "
                    + extraction.getExpenseNo() + ". Void that expense instead.");
        }
        extraction.setStatus(ExtractionStatus.DISMISSED);
        DocumentExtraction saved = documentExtractionRepository.save(extraction);
        auditService.log(MODULE, "DISMISS_CAPTURE", "documentExtraction#" + id,
                saved.getFileName() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public DocumentExtraction reopen(Long id) {
        DocumentExtraction extraction = documentExtractionRepository.findById(id).orElseThrow();
        if (extraction.isConverted()) {
            throw new IllegalStateException("A document that has already become an expense cannot "
                    + "be reopened — void " + extraction.getExpenseNo() + " instead");
        }
        extraction.setStatus(ExtractionStatus.PENDING);
        DocumentExtraction saved = documentExtractionRepository.save(extraction);
        auditService.log(MODULE, "REOPEN_CAPTURE", "documentExtraction#" + id, saved.getFileName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        DocumentExtraction extraction = documentExtractionRepository.findById(id).orElse(null);
        if (extraction == null) {
            return;
        }
        if (extraction.isConverted()) {
            throw new IllegalStateException("This document became " + extraction.getExpenseNo()
                    + ". Deleting the capture would leave that draft with nothing explaining "
                    + "where it came from.");
        }
        documentExtractionRepository.delete(extraction);
        auditService.log(MODULE, "DELETE_CAPTURE", "documentExtraction#" + id,
                extraction.getFileName());
    }

    public DocumentExtractionForm toForm(DocumentExtraction e) {
        return new DocumentExtractionForm(e.getSupplierName(), e.getVendorId(), e.getDocumentNo(),
                e.getDocumentDate(), e.getCurrencyCode(), e.getSubtotal(), e.getTaxAmount(),
                e.getTotal(), e.getExpenseAccountId(), e.getPaymentAccountId(), e.getNotes(), Boolean.FALSE);
    }

    private static ExtractionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ExtractionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal round(BigDecimal value) {
        return zero(value).setScale(2, RoundingMode.HALF_UP);
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

    public record CaptureSummary(long all, long pending, long ready, long converted, long dismissed,
                                 long waiting, boolean engineAvailable, String engineNote) {}
}
