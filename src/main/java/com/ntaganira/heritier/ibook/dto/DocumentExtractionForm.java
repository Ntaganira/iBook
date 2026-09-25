/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : DocumentExtractionForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Captured document form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentExtractionForm(
        @Size(max = 160) String supplierName,
        Long vendorId,
        @Size(max = 60) String documentNo,
        LocalDate documentDate,
        String currencyCode,
        @DecimalMin(value = "0.00", message = "{ocr.negative}") BigDecimal subtotal,
        @DecimalMin(value = "0.00", message = "{ocr.negative}") BigDecimal taxAmount,
        @DecimalMin(value = "0.00", message = "{ocr.negative}") BigDecimal total,
        Long expenseAccountId,
        Long paymentAccountId,
        @Size(max = 1000) String notes,
        /*
         * Boxed. A button that is not the one clicked submits nothing, and a record's canonical
         * constructor cannot take null for a primitive boolean — Spring then leaves the whole
         * model attribute null and the save silently does nothing, which is the fault already
         * recorded against the vendor and warehouse forms.
         */
        Boolean convertNow) {

    public boolean convertNowValue() {
        return Boolean.TRUE.equals(convertNow);
    }

    public BigDecimal subtotalValue() {
        return subtotal == null ? BigDecimal.ZERO : subtotal;
    }

    public BigDecimal taxAmountValue() {
        return taxAmount == null ? BigDecimal.ZERO : taxAmount;
    }

    public BigDecimal totalValue() {
        return total == null ? BigDecimal.ZERO : total;
    }
}
