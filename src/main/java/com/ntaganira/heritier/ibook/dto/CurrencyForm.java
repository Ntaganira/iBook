/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : CurrencyForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Currency form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CurrencyForm(
        @NotBlank(message = "{set.currency.code.required}")
        @Pattern(regexp = "[A-Z]{3}", message = "{set.currency.code.invalid}") String code,
        @NotBlank(message = "{set.currency.name.required}") String name,
        String symbol,
        @Min(value = 0, message = "{set.currency.decimals.invalid}")
        @Max(value = 4, message = "{set.currency.decimals.invalid}") int decimals,
        @DecimalMin(value = "0.000001", message = "{set.currency.rate.invalid}") BigDecimal exchangeRateToBase,
        boolean base,
        boolean enabled) {

    public CurrencyForm {
        if (exchangeRateToBase == null) {
            exchangeRateToBase = BigDecimal.ONE;
        }
    }
}