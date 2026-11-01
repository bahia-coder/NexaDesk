package com.nexadesk.user.dto;

import com.nexadesk.user.password.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire.")
        @Size(max = 128)
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
        @StrongPassword
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
