/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : WarehouseForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Warehouse form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseForm(
        @NotBlank(message = "{inv.wh.codeRequired}") @Size(max = 20) String code,
        @NotBlank(message = "{inv.wh.nameRequired}") @Size(max = 120) String name,
        @Size(max = 200) String location,
        boolean defaultLocation,
        boolean active) {
}
