package com.nexadesk.user.dto;

import com.nexadesk.user.RoleCode;

/** The authenticated user as seen by the frontend (no sensitive fields). */
public record CurrentUserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        RoleCode role,
        String department,
        String jobTitle,
        String avatarColor,
        boolean mustChangePassword) {
}
