/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : CategoryForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product category form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryForm(
        @NotBlank(message = "{inv.cat.codeRequired}") @Size(max = 20) String code,
        @NotBlank(message = "{inv.cat.nameRequired}") @Size(max = 120) String name,
        @Size(max = 300) String description,
        boolean active) {
}
