package com.nexadesk.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a user may change on their own profile; identity and role are managed by an administrator. */
public record UpdateProfileRequest(
        @Size(max = 120)
        String jobTitle,

        @Pattern(regexp = UserFieldRules.PHONE_REGEX, message = UserFieldRules.PHONE_MESSAGE)
        String phone,

        @Pattern(regexp = UserFieldRules.COLOR_REGEX, message = UserFieldRules.COLOR_MESSAGE)
        String avatarColor) {
}
