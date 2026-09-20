/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : AssetLocationForm.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset location form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssetLocationForm(
        @NotBlank(message = "{ast.loc.nameRequired}") @Size(max = 120) String name,
        @Size(max = 40) String code,
        @Size(max = 500) String description,
        @Size(max = 120) String site,
        @Size(max = 300) String address,
        @Size(max = 120) String city,
        @Size(max = 120) String manager,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean active) {

    public static AssetLocationForm empty() {
        return new AssetLocationForm(null, null, null, null, null, null, null, Boolean.TRUE);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }
}
