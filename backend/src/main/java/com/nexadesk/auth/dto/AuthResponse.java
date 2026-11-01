package com.nexadesk.auth.dto;

import com.nexadesk.user.dto.CurrentUserDto;
import java.time.Instant;

/** Body of login / refresh. The refresh token itself is only ever sent as an httpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, CurrentUserDto user) {

    public static AuthResponse bearer(String accessToken, Instant expiresAt, CurrentUserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, user);
    }
}
