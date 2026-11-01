package com.nexadesk.support;

import com.nexadesk.security.SecurityProperties;
import java.time.Duration;
import java.util.List;

public final class TestSecurityProperties {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    public static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private TestSecurityProperties() {
    }

    public static SecurityProperties create() {
        return new SecurityProperties(
                new SecurityProperties.Jwt("unused-in-unit-tests", "nexadesk", Duration.ofMinutes(15)),
                new SecurityProperties.RefreshToken(REFRESH_TTL, "nexadesk_refresh", true, REUSE_GRACE),
                new SecurityProperties.Lockout(MAX_ATTEMPTS, LOCK_DURATION),
                List.of("http://localhost:4200"));
    }
}
