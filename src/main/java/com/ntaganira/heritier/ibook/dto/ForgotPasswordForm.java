/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ForgotPasswordForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Forgot-password form DTO
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordForm(
        @NotBlank(message = "{forgot.email.required}")
        @Email(message = "{forgot.email.invalid}")
        String email
) {
}