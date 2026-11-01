package com.nexadesk.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "L'e-mail est obligatoire.")
        @Email(message = "L'e-mail n'est pas valide.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(max = 128)
        String password) {

    @Override
    public String toString() {
        // Never log the password.
        return "LoginRequest[email=" + email + "]";
    }
}
