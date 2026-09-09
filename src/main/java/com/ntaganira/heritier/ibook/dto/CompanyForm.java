/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : CompanyForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Company profile form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CompanyForm(
        @NotBlank(message = "{set.company.name.required}") String name,
        String legalName,
        String tin,
        @Email(message = "{set.company.email.invalid}") String email,
        String phone,
        String website,
        String address,
        String city,
        String country,
        @NotBlank(message = "{set.company.currency.required}") String currencyCode,
        String logoUrl,
        @NotBlank(message = "{set.company.fiscalStart.required}") String fiscalYearStart) {
}