/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ExciseDutyForm.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Excise duty form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExciseDutyForm(
        @NotBlank(message = "{exc.codeRequired}") @Size(max = 40) String code,
        @NotBlank(message = "{exc.nameRequired}") @Size(max = 160) String name,
        String basis,
        @DecimalMin(value = "0.0000", message = "{exc.negative}") BigDecimal rate,
        @DecimalMin(value = "0.00", message = "{exc.negative}") BigDecimal amountPerUnit,
        @Size(max = 40) String unitLabel,
        @Size(max = 300) String appliesTo,
        Long payableAccountId,
        @Size(max = 500) String rateSource,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean active) {

    public static ExciseDutyForm empty() {
        return new ExciseDutyForm(null, null, "PERCENT_OF_VALUE", BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, Boolean.TRUE);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }

    public BigDecimal rateValue() {
        return rate == null ? BigDecimal.ZERO : rate;
    }

    public BigDecimal amountPerUnitValue() {
        return amountPerUnit == null ? BigDecimal.ZERO : amountPerUnit;
    }
}
