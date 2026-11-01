package com.nexadesk.user.dto;

import com.nexadesk.user.RoleCode;
import java.time.Instant;

public record ProfileDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        RoleCode role,
        String department,
        String jobTitle,
        String phone,
        String avatarColor,
        Instant lastLoginAt) {
}
