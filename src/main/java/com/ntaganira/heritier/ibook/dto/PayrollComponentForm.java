/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : PayrollComponentForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Allowance and deduction form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayrollComponentForm(
        @NotBlank(message = "{pcm.codeRequired}") @Size(max = 40) String code,
        @NotBlank(message = "{pcm.nameRequired}") @Size(max = 160) String name,
        String kind,
        String calculation,
        @DecimalMin(value = "0.00", message = "{pcm.amountNegative}") BigDecimal amount,
        @DecimalMin(value = "0.00", message = "{pcm.percentNegative}") BigDecimal percent,
        Long accountId,
        @Size(max = 500) String description,
        Integer sortOrder,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean taxable,
        Boolean pensionable,
        Boolean active) {

    public static PayrollComponentForm empty(String kind) {
        return new PayrollComponentForm(null, null, kind == null ? "ALLOWANCE" : kind,
                "FIXED_AMOUNT", BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0,
                Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
    }

    public boolean taxableValue() {
        return Boolean.TRUE.equals(taxable);
    }

    public boolean pensionableValue() {
        return Boolean.TRUE.equals(pensionable);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }

    public BigDecimal amountValue() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public BigDecimal percentValue() {
        return percent == null ? BigDecimal.ZERO : percent;
    }
}
