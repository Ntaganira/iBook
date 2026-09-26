/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : PaymentRequestForm.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Payment request form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asking a customer to pay an invoice.
 *
 * <p>There is no amount validation beyond a lower bound here, because the ceiling is what is actually
 * outstanding on the invoice and only the service knows that. A blank amount means the whole of it.
 */
public record PaymentRequestForm(
        @NotNull(message = "{prq.invoiceRequired}") Long invoiceId,
        BigDecimal amount,
        @NotNull(message = "{prq.channelRequired}") String channel,
        @Size(max = 120) String payTo,
        @Size(max = 120) String reference,
        LocalDate requestedOn,
        @Size(max = 500) String notes) {

    public static PaymentRequestForm empty() {
        return new PaymentRequestForm(null, null, "MTN_MOMO", "", "", LocalDate.now(), "");
    }
}
