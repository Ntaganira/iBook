/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : BranchForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Branch form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BranchForm(
        @NotBlank(message = "{set.branch.name.required}") String name,
        @NotBlank(message = "{set.branch.code.required}")
        @Pattern(regexp = "[A-Za-z0-9-]{1,12}", message = "{set.branch.code.invalid}") String code,
        String address,
        String city,
        String country,
        String contactPerson,
        String phone,
        @Email(message = "{set.branch.email.invalid}") String email,
        boolean defaultBranch,
        boolean active) {
}