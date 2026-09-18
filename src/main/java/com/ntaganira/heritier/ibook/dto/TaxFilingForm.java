/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : TaxFilingForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Tax filing form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxFilingForm(
        @NotNull String filingType,
        @NotNull(message = "{tax.fil.periodRequired}")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
        @NotNull(message = "{tax.fil.periodRequired}")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
        BigDecimal declaredAmount,
        BigDecimal paidAmount,
        @Size(max = 120) String reference,
        @Size(max = 1000) String notes) {
}
