/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : CustomerForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CustomerForm(
        @NotBlank(message = "{cust.nameRequired}") @Size(max = 160) String name,
        @Size(max = 160) String companyName,
        @Email(message = "{cust.emailInvalid}") @Size(max = 160) String email,
        @Size(max = 40) String phone,
        @Size(max = 40) String mobileMoney,
        @Size(max = 40) String taxId,
        @Size(max = 200) String street,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 80) String country,
        @Size(max = 200) String website,
        @Size(max = 1000) String notes,
        String paymentTerms,
        BigDecimal openingBalance,
        boolean active) {

    public static CustomerForm empty() {
        return new CustomerForm(null, null, null, null, null, null, null, null, null, null,
                "Rwanda", null, null, "Net 15", BigDecimal.ZERO, true);
    }
}
