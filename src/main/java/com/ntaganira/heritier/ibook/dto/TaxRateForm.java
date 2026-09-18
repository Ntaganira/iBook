/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : TaxRateForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Tax rate form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TaxRateForm(
        @NotBlank(message = "{tax.cfg.codeRequired}") @Size(max = 20) String code,
        @NotBlank(message = "{tax.cfg.nameRequired}") @Size(max = 120) String name,
        @NotNull(message = "{tax.cfg.rateRequired}")
        @DecimalMin(value = "0.00", message = "{tax.cfg.rateRange}")
        @DecimalMax(value = "100.00", message = "{tax.cfg.rateRange}") BigDecimal rate,
        @NotNull String treatment,
        @Size(max = 300) String description,
        boolean defaultRate,
        boolean active) {

    public static TaxRateForm empty() {
        return new TaxRateForm("", "", BigDecimal.ZERO, "STANDARD", null, false, true);
    }
}
