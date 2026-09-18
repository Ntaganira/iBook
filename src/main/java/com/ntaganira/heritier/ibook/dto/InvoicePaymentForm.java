/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : InvoicePaymentForm.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Form payload for recording a payment against a sales invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoicePaymentForm(
        @NotNull LocalDate paymentDate,
        @NotNull BigDecimal amount,
        String method,
        String reference,
        @NotNull Long depositAccountId,
        String notes) {
}
