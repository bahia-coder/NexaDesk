package com.nexadesk.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identity fields an admin can edit. The role has its own endpoint (distinct rule and audit trail). */
public record UpdateUserRequest(
        @NotBlank(message = "L'e-mail est obligatoire.")
        @Email(message = "L'e-mail n'est pas valide.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 80)
        String lastName,

        @Size(max = 120)
        String jobTitle,

        @Pattern(regexp = UserFieldRules.PHONE_REGEX, message = UserFieldRules.PHONE_MESSAGE)
        String phone,

        Long departmentId) {
}
