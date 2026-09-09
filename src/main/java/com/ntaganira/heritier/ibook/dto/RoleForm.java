/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : RoleForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Role form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleForm(
        @NotBlank(message = "{set.role.name.required}") String name,
        String description,
        List<Long> permissionIds) {

    public List<Long> permissionIds() {
        return permissionIds == null ? List.of() : permissionIds;
    }
}