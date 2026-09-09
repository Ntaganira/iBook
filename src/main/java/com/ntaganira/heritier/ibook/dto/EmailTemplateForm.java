/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : EmailTemplateForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Email-template form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailTemplateForm(
        @NotBlank(message = "{set.template.code.required}")
        @Pattern(regexp = "[a-z0-9._-]+", message = "{set.template.code.invalid}") String code,
        @NotBlank(message = "{set.template.name.required}") String name,
        @NotBlank(message = "{set.template.subject.required}") String subject,
        String contentType,
        String body,
        boolean active) {
}