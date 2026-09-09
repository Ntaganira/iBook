/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ExchangeRateForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Exchange-rate form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateForm(
        @NotBlank(message = "{set.rate.base.required}") String baseCurrency,
        @NotBlank(message = "{set.rate.quote.required}") String quoteCurrency,
        @NotNull(message = "{set.rate.rate.required}")
        @DecimalMin(value = "0.000001", message = "{set.rate.rate.invalid}") BigDecimal rate,
        @NotNull(message = "{set.rate.date.required}")
        @PastOrPresent(message = "{set.rate.date.invalid}") LocalDate effectiveDate) {
}