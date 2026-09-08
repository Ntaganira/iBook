package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterForm(
        @NotBlank(message = "{register.firstName.required}")
        @Size(max = 60, message = "{register.firstName.long}")
        String firstName,

        @NotBlank(message = "{register.lastName.required}")
        @Size(max = 60, message = "{register.lastName.long}")
        String lastName,

        @NotBlank(message = "{register.email.required}")
        @Email(message = "{register.email.invalid}")
        String email,

        @Size(max = 20, message = "{register.phone.long}")
        String phone,

        @NotBlank(message = "{register.password.required}")
        @Size(min = 8, max = 100, message = "{register.password.length}")
        String password,

        @NotBlank(message = "{register.confirmPassword.required}")
        String confirmPassword
) {
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}