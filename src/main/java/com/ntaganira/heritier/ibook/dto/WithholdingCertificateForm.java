/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : WithholdingCertificateForm.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Withholding certificate form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WithholdingCertificateForm(
        @NotNull(message = "{wht.billRequired}") Long billId,
        @NotNull(message = "{wht.rateRequired}") Long rateId,
        @NotNull(message = "{wht.dateRequired}") LocalDate certificateDate,
        /*
         * The base is offered as the bill net of VAT and left editable, because only part of a
         * supply is sometimes subject to withholding and that is a judgement about the invoice.
         */
        @DecimalMin(value = "0.00", message = "{wht.negative}") BigDecimal baseAmount,
        @Size(max = 1000) String notes,
        /* Boxed: the button that was not clicked submits nothing, and a record cannot bind null
         * to a primitive boolean — the save would then silently do nothing. */
        Boolean issueNow) {

    public boolean issueNowValue() {
        return Boolean.TRUE.equals(issueNow);
    }

    public BigDecimal baseValue() {
        return baseAmount == null ? BigDecimal.ZERO : baseAmount;
    }
}
