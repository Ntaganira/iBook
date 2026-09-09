/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : InvoiceTemplateForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Invoice-template form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;

public record InvoiceTemplateForm(
        @NotBlank(message = "{set.template.name.required}") String name,
        @NotBlank(message = "{set.template.layout.required}") String layout,
        String accentColor,
        @NotBlank(message = "{set.template.paper.required}") String paperSize,
        boolean defaultTemplate,
        boolean showsLogo,
        boolean showsTaxSummary,
        boolean includesTerms,
        boolean active) {
}