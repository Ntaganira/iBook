/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ClaimInvoiceForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The invoice settings a progress claim is turned into a draft with
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.time.LocalDate;

public record ClaimInvoiceForm(LocalDate issueDate,
                               LocalDate dueDate,
                               Long taxRateId,
                               Long revenueAccountId,
                               String reference,
                               String customerMessage) {
}
