/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : EditUserForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : User edit form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;

public record EditUserForm(
        @NotBlank(message = "{set.user.firstName.required}") String firstName,
        @NotBlank(message = "{set.user.lastName.required}") String lastName,
        @NotBlank(message = "{set.user.email.required}") @Email(message = "{set.user.email.invalid}") String email,
        @NotBlank(message = "{set.user.username.required}") String username,
        String phone,
        Long branchId,
        @NotNull(message = "{set.user.role.required}") Long roleId,
        boolean enabled,
        @Size(min = 8, message = "{set.user.password.short}") String newPassword,
        String confirmPassword) {

    public boolean wantsPasswordChange() {
        return newPassword != null && !newPassword.isBlank();
    }

    public boolean passwordsMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}