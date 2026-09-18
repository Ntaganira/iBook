/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PurchaseOrderService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendor purchase orders and conversion into bills
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BillForm;
import com.ntaganira.heritier.ibook.dto.PurchaseOrderForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.PurchaseOrderStatus;
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
public class PurchaseOrderService {

    private static final String MODULE = "purchase-orders";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final TaxRateRepository taxRateRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final BillService billService;
    private final AuditService auditService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                VendorRepository vendorRepository,
                                AccountRepository accountRepository,
                                ProductRepository productRepository,
                                TaxRateRepository taxRateRepository,
                                NumberingSequenceRepository numberingSequenceRepository,
                                CompanyRepository companyRepository,
                                BillService billService,
                                AuditService auditService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.vendorRepository = vendorRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.taxRateRepository = taxRateRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.billService = billService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> list(String q, String status, Long vendorId, Pageable pageable) {
        return purchaseOrderRepository.search(trimToNull(q), parseStatus(status), vendorId, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) {
        return id == null ? null : purchaseOrderRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderSummary summary() {
        return new PurchaseOrderSummary(
                purchaseOrderRepository.count(),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DRAFT),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.CONFIRMED),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.BILLED),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.CANCELLED),
                purchaseOrderRepository.countLate(LocalDate.now()),
                zero(purchaseOrderRepository.totalFor(List.of(PurchaseOrderStatus.CONFIRMED))),
                zero(purchaseOrderRepository.totalFor(List.of(PurchaseOrderStatus.BILLED))));
    }

    // ----- Create / update -----

    @Transactional
    public PurchaseOrder save(PurchaseOrderForm form, Long id, String username) {
        PurchaseOrder order;
        if (id == null) {
            order = new PurchaseOrder();
            order.setOrderNo(nextOrderNo());
            order.setCreatedBy(username);
            order.setStatus(PurchaseOrderStatus.DRAFT);
        } else {
            order = purchaseOrderRepository.findById(id).orElseThrow();
            if (!order.isEditable()) {
                throw new IllegalStateException("This purchase order can no longer be edited");
            }
            order.getLines().clear();
        }

        Vendor vendor = vendorRepository.findById(form.getVendorId()).orElseThrow();
        order.setVendorId(vendor.getId());
        order.setVendorName(vendor.getName());
        order.setVendorEmail(vendor.getEmail());
        order.setOrderDate(form.getOrderDate() == null ? LocalDate.now() : form.getOrderDate());
        order.setExpectedDate(form.getExpectedDate());
        order.setReference(trimToNull(form.getReference()));
        order.setDeliveryAddress(trimToNull(form.getDeliveryAddress()));
        order.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        order.setMemo(trimToNull(form.getMemo()));
        order.setNotes(trimToNull(form.getNotes()));

        int sort = 0;
        for (BillForm.Line lineForm : form.filledLines()) {
            Product linked = lineForm.getProductId() == null ? null
                    : productRepository.findById(lineForm.getProductId()).orElse(null);
            TaxRate configured = lineForm.getTaxRateId() == null ? null
                    : taxRateRepository.findById(lineForm.getTaxRateId()).orElse(null);
            BigDecimal rate = configured != null ? zero(configured.getRate()) : lineForm.taxRateValue();
            Account expense = lineForm.getExpenseAccountId() == null ? null
                    : accountRepository.findById(lineForm.getExpenseAccountId()).orElse(null);
            BigDecimal base = lineForm.lineSubtotal();
            BigDecimal tax = taxOf(base, rate);
            order.addLine(PurchaseOrderLine.builder()
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
                    .expenseAccountId(expense == null ? null : expense.getId())
                    .expenseAccountCode(expense == null ? null : expense.getCode())
                    .expenseAccountName(expense == null ? null : expense.getName())
                    .sortOrder(sort++)
                    .build());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (PurchaseOrderLine l : order.getLines()) {
            subtotal = subtotal.add(zero(l.getLineSubtotal()));
        }
        BigDecimal discount = zero(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (PurchaseOrderLine l : order.getLines()) {
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

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        auditService.log(MODULE, id == null ? "CREATE_PURCHASE_ORDER" : "UPDATE_PURCHASE_ORDER",
                "purchaseOrder#" + saved.getId(), saved.getOrderNo() + " — " + saved.getVendorName());

        if (form.isConfirmNow() && saved.getStatus() == PurchaseOrderStatus.DRAFT) {
            saved = confirm(saved.getId());
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public PurchaseOrder confirm(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElseThrow();
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled purchase order cannot be confirmed");
        }
        if (order.getLines().isEmpty()) {
            throw new IllegalStateException("A purchase order with no line items cannot be confirmed");
        }
        if (order.getStatus() == PurchaseOrderStatus.DRAFT) {
            order.setStatus(PurchaseOrderStatus.CONFIRMED);
            order.setConfirmedAt(LocalDateTime.now());
            order = purchaseOrderRepository.save(order);
            auditService.log(MODULE, "CONFIRM_PURCHASE_ORDER", "purchaseOrder#" + id,
                    order.getOrderNo() + " confirmed");
        }
        return order;
    }

    @Transactional
    public PurchaseOrder cancel(Long id, String reason) {
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElseThrow();
        if (order.isConverted()) {
            throw new IllegalStateException("A billed purchase order cannot be cancelled");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledReason(trimToNull(reason));
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        auditService.log(MODULE, "CANCEL_PURCHASE_ORDER", "purchaseOrder#" + id,
                saved.getOrderNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public PurchaseOrder reopen(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElseThrow();
        if (order.getStatus() != PurchaseOrderStatus.CANCELLED) {
            return order;
        }
        order.setStatus(order.getConfirmedAt() == null
                ? PurchaseOrderStatus.DRAFT : PurchaseOrderStatus.CONFIRMED);
        order.setCancelledReason(null);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        auditService.log(MODULE, "REOPEN_PURCHASE_ORDER", "purchaseOrder#" + id,
                saved.getOrderNo() + " reopened");
        return saved;
    }

    /**
     * Turns a confirmed order into a draft bill by replaying its lines through
     * {@link BillService}, so numbering, tax resolution and totals stay in one place.
     * The order itself never touches the ledger.
     */
    @Transactional
    public Bill convertToBill(Long id, String username) {
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElseThrow();
        if (order.isConverted()) {
            throw new IllegalStateException("This purchase order has already been billed");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled purchase order cannot be billed");
        }
        if (order.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new IllegalStateException("Confirm the purchase order before billing it");
        }
        if (order.getLines().isEmpty()) {
            throw new IllegalStateException("A purchase order with no line items cannot be billed");
        }

        BillForm form = new BillForm();
        form.setVendorId(order.getVendorId());
        form.setBillDate(LocalDate.now());
        form.setDueDate(LocalDate.now().plusDays(30));
        form.setReference(order.getOrderNo());
        form.setCurrencyCode(order.getCurrencyCode());
        form.setDiscountAmount(order.getDiscountAmount());
        form.setMemo(order.getMemo());
        form.setPostNow(false);

        for (int i = 0; i < order.getLines().size(); i++) {
            PurchaseOrderLine src = order.getLines().get(i);
            BillForm.Line line = form.getLines().get(i);
            line.setDescription(src.getDescription());
            line.setProductId(src.getProductId());
            line.setQuantity(src.getQuantity());
            line.setUnitPrice(src.getUnitPrice());
            line.setTaxRate(src.getTaxRate());
            line.setTaxRateId(src.getTaxRateId());
            line.setExpenseAccountId(src.getExpenseAccountId());
        }

        Bill bill = billService.saveBill(form, null, username);

        order.setStatus(PurchaseOrderStatus.BILLED);
        order.setConvertedBillId(bill.getId());
        order.setConvertedBillNo(bill.getBillNo());
        purchaseOrderRepository.save(order);

        auditService.log(MODULE, "CONVERT_PURCHASE_ORDER", "purchaseOrder#" + id,
                order.getOrderNo() + " → " + bill.getBillNo());
        return bill;
    }

    @Transactional
    public void delete(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElse(null);
        if (order == null) {
            return;
        }
        if (order.isConverted()) {
            throw new IllegalStateException("A billed purchase order cannot be deleted");
        }
        purchaseOrderRepository.delete(order);
        auditService.log(MODULE, "DELETE_PURCHASE_ORDER", "purchaseOrder#" + id, order.getOrderNo());
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("PURCHASE_ORDER").orElse(null);
        return seq == null ? "PO-0001" : seq.previewNext();
    }

    private String nextOrderNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("PURCHASE_ORDER").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "PO-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "PO-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static PurchaseOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PurchaseOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record PurchaseOrderSummary(long all, long draft, long confirmed, long billed, long cancelled,
                                       long late, BigDecimal openValue, BigDecimal billedValue) {}
}
