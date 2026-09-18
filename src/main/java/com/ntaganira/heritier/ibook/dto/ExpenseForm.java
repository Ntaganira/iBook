/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ExpenseForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Direct expense form backing bean with editable line items
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseForm {

    private Long vendorId;
    private String payeeName;
    private LocalDate expenseDate;
    private Long paymentAccountId;
    private String paymentMethod;
    private String reference;
    private String currencyCode;
    private String memo;
    private String notes;
    private boolean postNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static ExpenseForm empty(BigDecimal defaultTaxRate) {
        ExpenseForm form = new ExpenseForm();
        form.setExpenseDate(LocalDate.now());
        form.setCurrencyCode("RWF");
        form.setPaymentMethod("CASH");
        for (int i = 0; i < 3; i++) {
            form.getLines().get(i).setTaxRate(defaultTaxRate);
        }
        return form;
    }

    public BigDecimal subtotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.amountValue());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal taxTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.lineTax());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal total() {
        return subtotal().add(taxTotal()).setScale(2, RoundingMode.HALF_UP);
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

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public Long getPaymentAccountId() {
        return paymentAccountId;
    }

    public void setPaymentAccountId(Long paymentAccountId) {
        this.paymentAccountId = paymentAccountId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
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

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
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
        private BigDecimal amount;
        private BigDecimal taxRate;
        private Long taxRateId;
        private Long expenseAccountId;

        public boolean isFilled() {
            return description != null && !description.isBlank() && amountValue().signum() != 0;
        }

        public BigDecimal amountValue() {
            return amount == null ? BigDecimal.ZERO : amount;
        }

        public BigDecimal taxRateValue() {
            return taxRate == null ? BigDecimal.ZERO : taxRate;
        }

        public BigDecimal lineTax() {
            return amountValue().multiply(taxRateValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        public BigDecimal lineTotal() {
            return amountValue().add(lineTax()).setScale(2, RoundingMode.HALF_UP);
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getTaxRate() {
            return taxRate;
        }

        public void setTaxRate(BigDecimal taxRate) {
            this.taxRate = taxRate;
        }

        public Long getTaxRateId() {
            return taxRateId;
        }

        public void setTaxRateId(Long taxRateId) {
            this.taxRateId = taxRateId;
        }

        public Long getExpenseAccountId() {
            return expenseAccountId;
        }

        public void setExpenseAccountId(Long expenseAccountId) {
            this.expenseAccountId = expenseAccountId;
        }
    }
}
