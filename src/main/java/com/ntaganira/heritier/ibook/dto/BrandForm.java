/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : BrandForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Brand form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandForm(
        @NotBlank(message = "{inv.brd.nameRequired}") @Size(max = 120) String name,
        @Size(max = 40) String code,
        @Size(max = 500) String description,
        @Size(max = 120) String manufacturer,
        @Size(max = 200) String website,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean active) {

    public static BrandForm empty() {
        return new BrandForm(null, null, null, null, null, Boolean.TRUE);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }
}
