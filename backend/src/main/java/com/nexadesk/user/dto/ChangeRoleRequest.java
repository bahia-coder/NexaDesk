package com.nexadesk.user.dto;

import com.nexadesk.user.RoleCode;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Le rôle est obligatoire.") RoleCode role) {
}
