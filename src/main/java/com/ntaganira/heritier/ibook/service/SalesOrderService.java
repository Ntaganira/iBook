/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : SalesOrderService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer sales orders and conversion into invoices
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.SalesOrderForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.SalesOrderStatus;
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
public class SalesOrderService {

    private static final String MODULE = "sales-orders";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String DEFAULT_REVENUE_CODE = "4002";

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final TaxRateRepository taxRateRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             CustomerRepository customerRepository,
                             AccountRepository accountRepository,
                             ProductRepository productRepository,
                             TaxRateRepository taxRateRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             CompanyRepository companyRepository,
                             InvoiceService invoiceService,
                             AuditService auditService) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.taxRateRepository = taxRateRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<SalesOrder> list(String q, String status, Long customerId, Pageable pageable) {
        return salesOrderRepository.search(trimToNull(q), parseStatus(status), customerId, pageable);
    }

    @Transactional(readOnly = true)
    public SalesOrder get(Long id) {
        return id == null ? null : salesOrderRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public SalesOrderSummary summary() {
        return new SalesOrderSummary(
                salesOrderRepository.count(),
                salesOrderRepository.countByStatus(SalesOrderStatus.DRAFT),
                salesOrderRepository.countByStatus(SalesOrderStatus.CONFIRMED),
                salesOrderRepository.countByStatus(SalesOrderStatus.INVOICED),
                salesOrderRepository.countByStatus(SalesOrderStatus.CANCELLED),
                salesOrderRepository.countLate(LocalDate.now()),
                zero(salesOrderRepository.totalFor(List.of(SalesOrderStatus.CONFIRMED))),
                zero(salesOrderRepository.totalFor(List.of(SalesOrderStatus.INVOICED))));
    }

    // ----- Create / update -----

    @Transactional
    public SalesOrder save(SalesOrderForm form, Long id, String username) {
        SalesOrder order;
        if (id == null) {
            order = new SalesOrder();
            order.setOrderNo(nextOrderNo());
            order.setCreatedBy(username);
            order.setStatus(SalesOrderStatus.DRAFT);
        } else {
            order = salesOrderRepository.findById(id).orElseThrow();
            if (!order.isEditable()) {
                throw new IllegalStateException("This sales order can no longer be edited");
            }
            order.getLines().clear();
        }

        Customer customer = customerRepository.findById(form.getCustomerId()).orElseThrow();
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerEmail(customer.getEmail());
        order.setOrderDate(form.getOrderDate() == null ? LocalDate.now() : form.getOrderDate());
        order.setExpectedDate(form.getExpectedDate());
        order.setReference(trimToNull(form.getReference()));
        order.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        order.setCustomerMessage(trimToNull(form.getCustomerMessage()));
        order.setNotes(trimToNull(form.getNotes()));

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
            order.addLine(SalesOrderLine.builder()
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
        for (SalesOrderLine l : order.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (SalesOrderLine l : order.getLines()) {
            BigDecimal base = zero(l.getLineSubtotal());
            if (base.signum() == 0) {
                continue;
            }
            BigDecimal share = (discount.signum() == 0 || subtotal.signum() == 0)
                    ? BigDecimal.ZERO
                    : discount.multiply(base).divide(subtotal, 2, RoundingMode.HALF_UP);
            taxTotal = taxTotal.add(taxOf(base.subtract(share), zero(l.getTaxRate())));
        }
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setTaxAmount(taxTotal);
        order.setTotal(subtotal.subtract(discount).add(taxTotal));

        SalesOrder saved = salesOrderRepository.save(order);
        auditService.log(MODULE, id == null ? "CREATE_SALES_ORDER" : "UPDATE_SALES_ORDER",
                "salesOrder#" + saved.getId(), saved.getOrderNo() + " — " + saved.getCustomerName());

        if (form.isConfirmNow() && saved.getStatus() == SalesOrderStatus.DRAFT) {
            saved = confirm(saved.getId());
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public SalesOrder confirm(Long id) {
        SalesOrder order = salesOrderRepository.findById(id).orElseThrow();
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled sales order cannot be confirmed");
        }
        if (order.getLines().isEmpty()) {
            throw new IllegalStateException("A sales order with no line items cannot be confirmed");
        }
        if (order.getStatus() == SalesOrderStatus.DRAFT) {
            order.setStatus(SalesOrderStatus.CONFIRMED);
            order.setConfirmedAt(LocalDateTime.now());
            order = salesOrderRepository.save(order);
            auditService.log(MODULE, "CONFIRM_SALES_ORDER", "salesOrder#" + id,
                    order.getOrderNo() + " confirmed");
        }
        return order;
    }

    @Transactional
    public SalesOrder cancel(Long id, String reason) {
        SalesOrder order = salesOrderRepository.findById(id).orElseThrow();
        if (order.isConverted()) {
            throw new IllegalStateException("An invoiced sales order cannot be cancelled");
        }
        order.setStatus(SalesOrderStatus.CANCELLED);
        order.setCancelledReason(trimToNull(reason));
        SalesOrder saved = salesOrderRepository.save(order);
        auditService.log(MODULE, "CANCEL_SALES_ORDER", "salesOrder#" + id,
                saved.getOrderNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public SalesOrder reopen(Long id) {
        SalesOrder order = salesOrderRepository.findById(id).orElseThrow();
        if (order.getStatus() != SalesOrderStatus.CANCELLED) {
            return order;
        }
        order.setStatus(order.getConfirmedAt() == null
                ? SalesOrderStatus.DRAFT : SalesOrderStatus.CONFIRMED);
        order.setCancelledReason(null);
        SalesOrder saved = salesOrderRepository.save(order);
        auditService.log(MODULE, "REOPEN_SALES_ORDER", "salesOrder#" + id, saved.getOrderNo() + " reopened");
        return saved;
    }

    /**
     * Turns a confirmed order into a draft invoice by replaying its lines through
     * {@link InvoiceService}, so numbering, tax resolution and totals stay in one place.
     * The order itself never touches the ledger.
     */
    @Transactional
    public Invoice convertToInvoice(Long id, String username) {
        SalesOrder order = salesOrderRepository.findById(id).orElseThrow();
        if (order.isConverted()) {
            throw new IllegalStateException("This sales order has already been invoiced");
        }
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled sales order cannot be invoiced");
        }
        if (order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new IllegalStateException("Confirm the sales order before invoicing it");
        }
        if (order.getLines().isEmpty()) {
            throw new IllegalStateException("A sales order with no line items cannot be invoiced");
        }

        InvoiceForm form = new InvoiceForm();
        form.setCustomerId(order.getCustomerId());
        form.setIssueDate(LocalDate.now());
        form.setDueDate(LocalDate.now().plusDays(30));
        form.setReference(order.getOrderNo());
        form.setCurrencyCode(order.getCurrencyCode());
        form.setDiscountAmount(order.getDiscountAmount());
        form.setCustomerMessage(order.getCustomerMessage());
        form.setPostNow(false);

        for (int i = 0; i < order.getLines().size(); i++) {
            SalesOrderLine src = order.getLines().get(i);
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

        order.setStatus(SalesOrderStatus.INVOICED);
        order.setConvertedInvoiceId(invoice.getId());
        order.setConvertedInvoiceNo(invoice.getInvoiceNo());
        salesOrderRepository.save(order);

        auditService.log(MODULE, "CONVERT_SALES_ORDER", "salesOrder#" + id,
                order.getOrderNo() + " → " + invoice.getInvoiceNo());
        return invoice;
    }

    @Transactional
    public void delete(Long id) {
        SalesOrder order = salesOrderRepository.findById(id).orElse(null);
        if (order == null) {
            return;
        }
        if (order.isConverted()) {
            throw new IllegalStateException("An invoiced sales order cannot be deleted");
        }
        salesOrderRepository.delete(order);
        auditService.log(MODULE, "DELETE_SALES_ORDER", "salesOrder#" + id, order.getOrderNo());
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("SALE_ORDER").orElse(null);
        return seq == null ? "SO-0001" : seq.previewNext();
    }

    private String nextOrderNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("SALE_ORDER").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "SO-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "SO-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static SalesOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SalesOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record SalesOrderSummary(long all, long draft, long confirmed, long invoiced, long cancelled,
                                    long late, BigDecimal openValue, BigDecimal invoicedValue) {}
}
