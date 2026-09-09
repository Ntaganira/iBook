/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : SecuritySettingsForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Security settings form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;

public record SecuritySettingsForm(
        boolean twoFactorRequired,
        @Min(value = 8, message = "{set.security.minLength.invalid}")
        @Max(value = 32, message = "{set.security.minLength.invalid}") int passwordMinLength,
        boolean requireUppercase,
        boolean requireNumber,
        boolean requireSpecialChar,
        @Min(value = 0, message = "{set.security.expiry.invalid}") int passwordExpiryDays,
        @Min(value = 1, message = "{set.security.attempts.invalid}") int maxLoginAttempts,
        @Min(value = 1, message = "{set.security.lockout.invalid}") int lockoutMinutes,
        @Min(value = 5, message = "{set.security.session.invalid}") int sessionTimeoutMinutes,
        boolean allowPublicSignup,
        @NotBlank(message = "{set.security.defaultRole.required}") String defaultRole,
        boolean ipWhitelistEnabled) {
}