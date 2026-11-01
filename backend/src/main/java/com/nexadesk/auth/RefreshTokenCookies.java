package com.nexadesk.auth;

import com.nexadesk.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The refresh-token cookie: httpOnly (unreadable by JavaScript), SameSite=Strict (never sent cross-site)
 * and scoped to {@code /api/auth} (not attached to every API call).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    static final String PATH = "/api/auth";

    private final SecurityProperties properties;

    public ResponseCookie create(String rawToken) {
        return base(rawToken, properties.refreshToken().ttl());
    }

    public ResponseCookie clear() {
        return base("", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.refreshToken().cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie base(String value, Duration maxAge) {
        return ResponseCookie.from(properties.refreshToken().cookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshToken().cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
