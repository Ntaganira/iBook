/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : SalesOrderForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Sales order form backing bean, sharing InvoiceForm's line shape
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesOrderForm {

    private Long customerId;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private String reference;
    private String currencyCode;
    private BigDecimal discountAmount;
    private String customerMessage;
    private String notes;
    private boolean confirmNow;
    private List<InvoiceForm.Line> lines = new AutoPopulatingList<>(InvoiceForm.Line.class);

    public static SalesOrderForm empty(BigDecimal defaultTaxRate) {
        SalesOrderForm form = new SalesOrderForm();
        form.setOrderDate(LocalDate.now());
        form.setExpectedDate(LocalDate.now().plusDays(14));
        form.setCurrencyCode("RWF");
        form.setDiscountAmount(BigDecimal.ZERO);
        for (int i = 0; i < 3; i++) {
            InvoiceForm.Line line = form.getLines().get(i);
            line.setQuantity(BigDecimal.ONE);
            line.setTaxRate(defaultTaxRate);
        }
        return form;
    }

    public BigDecimal subtotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (InvoiceForm.Line line : lines) {
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
            for (InvoiceForm.Line line : lines) {
                BigDecimal base = line.lineSubtotal();
                if (base.signum() == 0) {
                    continue;
                }
                BigDecimal share = discount.signum() == 0
                        ? BigDecimal.ZERO
                        : discount.multiply(base).divide(sub, 2, RoundingMode.HALF_UP);
                total = total.add(base.subtract(share).multiply(line.taxRateValue())
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
        for (InvoiceForm.Line line : lines) {
            if (line.isFilled()) {
                return true;
            }
        }
        return false;
    }

    public List<InvoiceForm.Line> filledLines() {
        List<InvoiceForm.Line> filled = new ArrayList<>();
        if (lines != null) {
            for (InvoiceForm.Line line : lines) {
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

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
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

    public boolean isConfirmNow() {
        return confirmNow;
    }

    public void setConfirmNow(boolean confirmNow) {
        this.confirmNow = confirmNow;
    }

    public List<InvoiceForm.Line> getLines() {
        return lines;
    }

    public void setLines(List<InvoiceForm.Line> lines) {
        this.lines = new AutoPopulatingList<>(InvoiceForm.Line.class);
        if (lines != null) {
            this.lines.addAll(lines);
        }
    }
}
