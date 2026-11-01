package com.nexadesk.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code nexadesk.security.*} — validated at startup so a missing secret fails fast. */
@Validated
@ConfigurationProperties("nexadesk.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Lockout lockout,
        @NotEmpty List<String> allowedOrigins) {

    /**
     * @param secret base64-encoded HMAC key, at least 256 bits once decoded
     */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {
    }

    /**
     * @param reuseGrace a rotated token presented again within this window is treated as a concurrent
     *                   refresh (several tabs) rather than as theft
     */
    public record RefreshToken(@NotNull Duration ttl, @NotBlank String cookieName, boolean cookieSecure,
                               @NotNull Duration reuseGrace) {
    }

    public record Lockout(@Min(1) int maxAttempts, @NotNull Duration duration) {
    }
}
