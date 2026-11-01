package com.nexadesk.user.dto;

import com.nexadesk.user.RoleCode;
import java.time.Instant;

/** Administration view of an account (list and detail). */
public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        RoleCode role,
        Long departmentId,
        String departmentName,
        String jobTitle,
        String phone,
        String avatarColor,
        boolean active,
        boolean mustChangePassword,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {
}
