package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordForm(
        @NotBlank(message = "{forgot.email.required}")
        @Email(message = "{forgot.email.invalid}")
        String email
) {
}