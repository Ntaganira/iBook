/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : CreditNoteForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Credit note form backing bean, sharing InvoiceForm's line shape
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreditNoteForm {

    private Long customerId;
    private Long invoiceId;
    private LocalDate creditDate;
    private String reference;
    private String reason;
    private String currencyCode;
    private BigDecimal discountAmount;
    private String customerMessage;
    private String notes;
    private boolean restockItems = true;
    private boolean postNow;
    private List<InvoiceForm.Line> lines = new AutoPopulatingList<>(InvoiceForm.Line.class);

    public static CreditNoteForm empty(BigDecimal defaultTaxRate) {
        CreditNoteForm form = new CreditNoteForm();
        form.setCreditDate(LocalDate.now());
        form.setCurrencyCode("RWF");
        form.setDiscountAmount(BigDecimal.ZERO);
        form.setRestockItems(true);
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

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public LocalDate getCreditDate() {
        return creditDate;
    }

    public void setCreditDate(LocalDate creditDate) {
        this.creditDate = creditDate;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public boolean isRestockItems() {
        return restockItems;
    }

    public void setRestockItems(boolean restockItems) {
        this.restockItems = restockItems;
    }

    public boolean isPostNow() {
        return postNow;
    }

    public void setPostNow(boolean postNow) {
        this.postNow = postNow;
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
