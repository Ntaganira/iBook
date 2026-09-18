/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : InvoiceForm.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Sales invoice form backing bean with editable line items
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvoiceForm {

    public static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("18.00");

    private Long customerId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String paymentTerms;
    private String reference;
    private String currencyCode;
    private BigDecimal discountAmount;
    private String customerMessage;
    private String notes;
    private boolean postNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static InvoiceForm empty() {
        InvoiceForm form = new InvoiceForm();
        form.setIssueDate(LocalDate.now());
        form.setDueDate(LocalDate.now().plusDays(30));
        form.setPaymentTerms("NET30");
        form.setCurrencyCode("RWF");
        form.setDiscountAmount(BigDecimal.ZERO);
        for (int i = 0; i < 3; i++) {
            Line line = form.getLines().get(i);
            line.setQuantity(BigDecimal.ONE);
            line.setTaxRate(DEFAULT_VAT_RATE);
        }
        return form;
    }

    public BigDecimal subtotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.lineSubtotal());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal discountValue() {
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        BigDecimal sub = subtotal();
        return discount.compareTo(sub) > 0 ? sub : discount;
    }

    public BigDecimal taxTotal() {
        BigDecimal sub = subtotal();
        if (sub.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = discountValue();
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                BigDecimal base = line.lineSubtotal();
                if (base.signum() == 0) {
                    continue;
                }
                BigDecimal share = discount.signum() == 0
                        ? BigDecimal.ZERO
                        : discount.multiply(base).divide(sub, 2, RoundingMode.HALF_UP);
                BigDecimal taxable = base.subtract(share);
                total = total.add(taxable.multiply(line.taxRateValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal total() {
        return subtotal().subtract(discountValue()).add(taxTotal()).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean hasContent() {
        if (lines == null) {
            return false;
        }
        for (Line line : lines) {
            if (line.isFilled()) {
                return true;
            }
        }
        return false;
    }

    public List<Line> filledLines() {
        List<Line> filled = new ArrayList<>();
        if (lines != null) {
            for (Line line : lines) {
                if (line.isFilled()) {
                    filled.add(line);
                }
            }
        }
        return filled;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getCustomerMessage() {
        return customerMessage;
    }

    public void setCustomerMessage(String customerMessage) {
        this.customerMessage = customerMessage;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isPostNow() {
        return postNow;
    }

    public void setPostNow(boolean postNow) {
        this.postNow = postNow;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = new AutoPopulatingList<>(Line.class);
        if (lines != null) {
            this.lines.addAll(lines);
        }
    }

    public static class Line {

        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private Long revenueAccountId;
        private Long taxRateId;
        private Long productId;

        public boolean isFilled() {
            return description != null && !description.isBlank() && unitPriceValue().signum() != 0;
        }

        public BigDecimal quantityValue() {
            return quantity == null ? BigDecimal.ZERO : quantity;
        }

        public BigDecimal unitPriceValue() {
            return unitPrice == null ? BigDecimal.ZERO : unitPrice;
        }

        public BigDecimal taxRateValue() {
            return taxRate == null ? BigDecimal.ZERO : taxRate;
        }

        public BigDecimal lineSubtotal() {
            return quantityValue().multiply(unitPriceValue()).setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal lineTax() {
            return lineSubtotal().multiply(taxRateValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        public BigDecimal lineTotal() {
            return lineSubtotal().add(lineTax());
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getTaxRate() {
            return taxRate;
        }

        public void setTaxRate(BigDecimal taxRate) {
            this.taxRate = taxRate;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Long getTaxRateId() {
            return taxRateId;
        }

        public void setTaxRateId(Long taxRateId) {
            this.taxRateId = taxRateId;
        }

        public Long getRevenueAccountId() {
            return revenueAccountId;
        }

        public void setRevenueAccountId(Long revenueAccountId) {
            this.revenueAccountId = revenueAccountId;
        }
    }
}
