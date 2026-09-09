/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : NumberingForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Numbering-sequence form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;

public record NumberingForm(
        @NotBlank(message = "{set.numbering.name.required}") String name,
        @NotBlank(message = "{set.numbering.type.required}") String docType,
        String prefix,
        String suffix,
        @Min(value = 1, message = "{set.numbering.padding.invalid}")
        @Max(value = 12, message = "{set.numbering.padding.invalid}") int padding,
        @Min(value = 1, message = "{set.numbering.next.invalid}") long nextNumber,
        boolean resetYearly,
        boolean active) {

    public NumberingForm {
        if (padding < 1) padding = 4;
        if (nextNumber < 1) nextNumber = 1;
    }
}