/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : EstimateService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer estimates and conversion into invoices
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.EstimateForm;
import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.EstimateStatus;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class EstimateService {

    private static final String MODULE = "estimates";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EstimateRepository estimateRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final TaxRateRepository taxRateRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public EstimateService(EstimateRepository estimateRepository,
                           CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           ProductRepository productRepository,
                           TaxRateRepository taxRateRepository,
                           NumberingSequenceRepository numberingSequenceRepository,
                           CompanyRepository companyRepository,
                           InvoiceService invoiceService,
                           AuditService auditService) {
        this.estimateRepository = estimateRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.taxRateRepository = taxRateRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Estimate> list(String q, String status, Long customerId, Pageable pageable) {
        EstimateStatus st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = EstimateStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                st = null;
            }
        }
        return estimateRepository.search(trimToNull(q), st, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Estimate get(Long id) {
        return id == null ? null : estimateRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    @Transactional(readOnly = true)
    public EstimateSummary summary() {
        return new EstimateSummary(
                estimateRepository.count(),
                estimateRepository.countByStatus(EstimateStatus.DRAFT),
                estimateRepository.countByStatus(EstimateStatus.SENT),
                estimateRepository.countByStatus(EstimateStatus.ACCEPTED),
                estimateRepository.countByStatus(EstimateStatus.CONVERTED),
                estimateRepository.countExpired(LocalDate.now()),
                zero(estimateRepository.totalFor(List.of(EstimateStatus.SENT, EstimateStatus.ACCEPTED))),
                zero(estimateRepository.totalFor(List.of(EstimateStatus.ACCEPTED))));
    }

    // ----- Create / update -----

    @Transactional
    public Estimate save(EstimateForm form, Long id, String username) {
        Estimate estimate;
        if (id == null) {
            estimate = new Estimate();
            estimate.setEstimateNo(nextEstimateNo());
            estimate.setCreatedBy(username);
            estimate.setStatus(EstimateStatus.DRAFT);
        } else {
            estimate = estimateRepository.findById(id).orElseThrow();
            if (!estimate.isEditable()) {
                throw new IllegalStateException("This estimate can no longer be edited");
            }
            estimate.getLines().clear();
        }

        Customer customer = customerRepository.findById(form.getCustomerId()).orElseThrow();
        estimate.setCustomerId(customer.getId());
        estimate.setCustomerName(customer.getName());
        estimate.setCustomerEmail(customer.getEmail());
        estimate.setEstimateDate(form.getEstimateDate() == null ? LocalDate.now() : form.getEstimateDate());
        estimate.setExpiryDate(form.getExpiryDate());
        estimate.setReference(trimToNull(form.getReference()));
        estimate.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        estimate.setCustomerMessage(trimToNull(form.getCustomerMessage()));
        estimate.setNotes(trimToNull(form.getNotes()));

        int order = 0;
        for (InvoiceForm.Line lineForm : form.filledLines()) {
            Product linked = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);
            TaxRate configured = lineForm.getTaxRateId() == null ? null
                    : taxRateRepository.findById(lineForm.getTaxRateId()).orElse(null);
            BigDecimal rate = configured != null ? zero(configured.getRate()) : lineForm.taxRateValue();
            Account revenue = lineForm.getRevenueAccountId() == null ? null
                    : accountRepository.findById(lineForm.getRevenueAccountId()).orElse(null);
            BigDecimal base = lineForm.lineSubtotal();
            BigDecimal tax = base.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            estimate.addLine(EstimateLine.builder()
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
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (EstimateLine l : estimate.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (EstimateLine l : estimate.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            if (base.signum() == 0) {
                continue;
            }
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            taxTotal = taxTotal.add(base.subtract(share).multiply(zero(l.getTaxRate()))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }
        estimate.setSubtotal(subtotal);
        estimate.setDiscountAmount(discount);
        estimate.setTaxAmount(taxTotal);
        estimate.setTotal(subtotal.subtract(discount).add(taxTotal));

        Estimate saved = estimateRepository.save(estimate);
        auditService.log(MODULE, id == null ? "CREATE_ESTIMATE" : "UPDATE_ESTIMATE",
                "estimate#" + saved.getId(), saved.getEstimateNo() + " \u2014 " + saved.getCustomerName());

        if (form.isSendNow() && saved.getStatus() == EstimateStatus.DRAFT) {
            saved = markSent(saved.getId());
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public Estimate markSent(Long id) {
        Estimate e = estimateRepository.findById(id).orElseThrow();
        if (e.getStatus() == EstimateStatus.DRAFT) {
            e.setStatus(EstimateStatus.SENT);
            e.setSentAt(LocalDateTime.now());
            e = estimateRepository.save(e);
            auditService.log(MODULE, "SEND_ESTIMATE", "estimate#" + id, e.getEstimateNo() + " sent");
        }
        return e;
    }

    @Transactional
    public Estimate decide(Long id, boolean accepted) {
        Estimate e = estimateRepository.findById(id).orElseThrow();
        if (e.isConverted()) {
            throw new IllegalStateException("A converted estimate cannot be changed");
        }
        e.setStatus(accepted ? EstimateStatus.ACCEPTED : EstimateStatus.DECLINED);
        e.setDecidedAt(LocalDate.now());
        Estimate saved = estimateRepository.save(e);
        auditService.log(MODULE, accepted ? "ACCEPT_ESTIMATE" : "DECLINE_ESTIMATE",
                "estimate#" + id, saved.getEstimateNo());
        return saved;
    }

    /**
     * Turns an accepted estimate into a draft invoice by replaying its lines through
     * {@link InvoiceService}, so numbering, tax resolution and totals stay in one place.
     */
    @Transactional
    public Invoice convertToInvoice(Long id, String username) {
        Estimate estimate = estimateRepository.findById(id).orElseThrow();
        if (estimate.isConverted()) {
            throw new IllegalStateException("This estimate has already been converted");
        }
        if (estimate.getStatus() == EstimateStatus.DECLINED) {
            throw new IllegalStateException("A declined estimate cannot be converted");
        }
        if (estimate.getLines().isEmpty()) {
            throw new IllegalStateException("An estimate with no line items cannot be converted");
        }

        InvoiceForm form = new InvoiceForm();
        form.setCustomerId(estimate.getCustomerId());
        form.setIssueDate(LocalDate.now());
        form.setDueDate(LocalDate.now().plusDays(30));
        form.setReference(estimate.getEstimateNo());
        form.setCurrencyCode(estimate.getCurrencyCode());
        form.setDiscountAmount(estimate.getDiscountAmount());
        form.setCustomerMessage(estimate.getCustomerMessage());
        form.setPostNow(false);

        for (int i = 0; i < estimate.getLines().size(); i++) {
            EstimateLine src = estimate.getLines().get(i);
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

        estimate.setStatus(EstimateStatus.CONVERTED);
        estimate.setConvertedInvoiceId(invoice.getId());
        estimate.setConvertedInvoiceNo(invoice.getInvoiceNo());
        estimateRepository.save(estimate);

        auditService.log(MODULE, "CONVERT_ESTIMATE", "estimate#" + id,
                estimate.getEstimateNo() + " \u2192 " + invoice.getInvoiceNo());
        return invoice;
    }

    @Transactional
    public void delete(Long id) {
        Estimate e = estimateRepository.findById(id).orElse(null);
        if (e == null) {
            return;
        }
        if (e.isConverted()) {
            throw new IllegalStateException("A converted estimate cannot be deleted");
        }
        estimateRepository.delete(e);
        auditService.log(MODULE, "DELETE_ESTIMATE", "estimate#" + id, e.getEstimateNo());
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("ESTIMATE").orElse(null);
        return seq == null ? "EST-0001" : seq.previewNext();
    }

    private String nextEstimateNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("ESTIMATE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "EST-" : seq.getPrefix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next);
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "EST-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    public record EstimateSummary(long all, long draft, long sent, long accepted, long converted,
                                  long expired, BigDecimal openValue, BigDecimal acceptedValue) {}
}
