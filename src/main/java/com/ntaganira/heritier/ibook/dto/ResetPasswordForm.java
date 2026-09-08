package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordForm(
        @NotBlank(message = "{reset.password.required}")
        @Size(min = 8, max = 100, message = "{reset.password.length}")
        String password,

        @NotBlank(message = "{reset.confirmPassword.required}")
        String confirmPassword
) {
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}