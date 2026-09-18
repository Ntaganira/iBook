/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ContractorForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Contractor form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractorForm(
        @NotBlank(message = "{con.nameRequired}") @Size(max = 160) String name,
        String contractorType,
        @Size(max = 120) String trade,
        @Email(message = "{con.emailInvalid}") @Size(max = 160) String email,
        @Size(max = 40) String phone,
        @Size(max = 40) String mobileMoney,
        @Size(max = 40) String taxId,
        @Size(max = 200) String street,
        @Size(max = 80) String city,
        @Size(max = 80) String country,
        @Size(max = 120) String bankName,
        @Size(max = 60) String bankAccount,
        LocalDate contractStart,
        LocalDate contractEnd,
        String rateType,
        @DecimalMin(value = "0.00", message = "{con.rateNegative}") BigDecimal rateAmount,
        @DecimalMin(value = "0.00", message = "{con.withholdingRange}")
        @DecimalMax(value = "100.00", message = "{con.withholdingRange}") BigDecimal withholdingRate,
        Long vendorId,
        @Size(max = 1000) String notes,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean active) {

    public static ContractorForm empty() {
        return new ContractorForm(null, "INDIVIDUAL", null, null, null, null, null, null, null,
                "Rwanda", null, null, LocalDate.now(), null, "FIXED", BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, Boolean.TRUE);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }
}
