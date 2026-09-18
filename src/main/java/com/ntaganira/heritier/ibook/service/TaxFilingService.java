/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : TaxFilingService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Recording and tracking tax declarations
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.TaxFilingForm;
import com.ntaganira.heritier.ibook.entity.TaxFiling;
import com.ntaganira.heritier.ibook.enums.FilingStatus;
import com.ntaganira.heritier.ibook.enums.TaxFilingType;
import com.ntaganira.heritier.ibook.repository.TaxFilingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class TaxFilingService {

    private static final String MODULE = "taxes";

    /** Rwanda VAT returns are due by the 15th of the month after the period. */
    private static final int VAT_DUE_DAY = 15;

    private final TaxFilingRepository filingRepository;
    private final TaxService taxService;
    private final AuditService auditService;

    public TaxFilingService(TaxFilingRepository filingRepository,
                            TaxService taxService,
                            AuditService auditService) {
        this.filingRepository = filingRepository;
        this.taxService = taxService;
        this.auditService = auditService;
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

    @Transactional(readOnly = true)
    public Page<TaxFiling> list(String type, String status, Pageable pageable) {
        return filingRepository.search(parseType(type), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public TaxFiling get(Long id) {
        return id == null ? null : filingRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean periodAlreadyFiled(TaxFilingType type, LocalDate from, LocalDate to, Long excludeId) {
        return filingRepository.findByFilingTypeAndPeriodFromAndPeriodTo(type, from, to)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .isPresent();
    }

    /** Default due date for a period; only VAT has a rule we can rely on. */
    public static LocalDate defaultDueDate(TaxFilingType type, LocalDate periodTo) {
        if (periodTo == null) {
            return null;
        }
        if (type == TaxFilingType.VAT || type == TaxFilingType.PAYE) {
            LocalDate nextMonth = periodTo.plusMonths(1);
            int day = Math.min(VAT_DUE_DAY, nextMonth.lengthOfMonth());
            return nextMonth.withDayOfMonth(day);
        }
        return periodTo.plusMonths(1);
    }

    /** Net VAT for a period, used to prefill a new VAT filing. */
    @Transactional(readOnly = true)
    public BigDecimal suggestedAmount(TaxFilingType type, LocalDate from, LocalDate to) {
        if (type != TaxFilingType.VAT || from == null || to == null) {
            return BigDecimal.ZERO;
        }
        return taxService.vatReturn(from, to).netVat();
    }

    @Transactional
    public TaxFiling save(TaxFilingForm form, Long id, String username) {
        TaxFiling filing = id == null ? new TaxFiling() : filingRepository.findById(id).orElseThrow();
        if (id != null && !filing.isEditable()) {
            throw new IllegalStateException("Only draft filings can be edited");
        }
        if (id == null) {
            filing.setCreatedBy(username);
            filing.setStatus(FilingStatus.DRAFT);
        }
        TaxFilingType type = parseType(form.filingType());
        filing.setFilingType(type == null ? TaxFilingType.VAT : type);
        filing.setPeriodFrom(form.periodFrom());
        filing.setPeriodTo(form.periodTo());
        filing.setDueDate(form.dueDate() != null
                ? form.dueDate()
                : defaultDueDate(filing.getFilingType(), form.periodTo()));
        filing.setDeclaredAmount(zero(form.declaredAmount()));
        filing.setPaidAmount(zero(form.paidAmount()));
        filing.setReference(trimToNull(form.reference()));
        filing.setNotes(trimToNull(form.notes()));
        TaxFiling saved = filingRepository.save(filing);
        auditService.log(MODULE, id == null ? "CREATE_FILING" : "UPDATE_FILING",
                "filing#" + saved.getId(),
                saved.getFilingType().name() + " " + saved.getPeriodLabel());
        return saved;
    }

    @Transactional
    public TaxFiling submit(Long id, String reference) {
        TaxFiling filing = filingRepository.findById(id).orElseThrow();
        if (filing.getStatus() != FilingStatus.DRAFT) {
            return filing;
        }
        filing.setStatus(FilingStatus.SUBMITTED);
        filing.setSubmittedAt(LocalDate.now());
        if (trimToNull(reference) != null) {
            filing.setReference(reference.trim());
        }
        TaxFiling saved = filingRepository.save(filing);
        auditService.log(MODULE, "SUBMIT_FILING", "filing#" + id,
                saved.getFilingType().name() + " " + saved.getPeriodLabel() + " submitted");
        return saved;
    }

    @Transactional
    public TaxFiling recordPayment(Long id, BigDecimal amount, LocalDate paidOn) {
        TaxFiling filing = filingRepository.findById(id).orElseThrow();
        if (filing.getStatus() == FilingStatus.DRAFT) {
            throw new IllegalStateException("Submit the filing before recording a payment");
        }
        BigDecimal value = zero(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        filing.setPaidAmount(zero(filing.getPaidAmount()).add(value));
        if (filing.getBalanceDue().signum() == 0) {
            filing.setStatus(FilingStatus.PAID);
            filing.setPaidAt(paidOn == null ? LocalDate.now() : paidOn);
        }
        TaxFiling saved = filingRepository.save(filing);
        auditService.log(MODULE, "PAY_FILING", "filing#" + id,
                saved.getFilingType().name() + " payment " + value.toPlainString());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        TaxFiling filing = filingRepository.findById(id).orElse(null);
        if (filing == null) {
            return;
        }
        if (!filing.isEditable()) {
            throw new IllegalStateException("Only draft filings can be deleted");
        }
        filingRepository.delete(filing);
        auditService.log(MODULE, "DELETE_FILING", "filing#" + id,
                filing.getFilingType().name() + " " + filing.getPeriodLabel() + " deleted");
    }

    @Transactional(readOnly = true)
    public FilingSummary summary() {
        return new FilingSummary(
                filingRepository.count(),
                filingRepository.countByStatus(FilingStatus.DRAFT),
                filingRepository.countByStatus(FilingStatus.SUBMITTED),
                filingRepository.countByStatus(FilingStatus.PAID),
                zero(filingRepository.totalOutstanding()),
                filingRepository.findOverdue(LocalDate.now()).size());
    }

    private static TaxFilingType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaxFilingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static FilingStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FilingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<TaxFiling> overdue() {
        return filingRepository.findOverdue(LocalDate.now());
    }

    public record FilingSummary(long all, long draft, long submitted, long paid,
                                BigDecimal outstanding, long overdue) {}
}
