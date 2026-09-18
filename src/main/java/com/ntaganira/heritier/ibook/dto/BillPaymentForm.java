/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : BillPaymentForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Form payload for recording a payment against a vendor bill
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillPaymentForm(
        @NotNull LocalDate paymentDate,
        @NotNull BigDecimal amount,
        String method,
        String reference,
        @NotNull Long paidFromAccountId,
        String notes) {
}
