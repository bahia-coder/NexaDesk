package com.nexadesk.security;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Defense in depth for the cookie-authenticated endpoints (refresh, logout): a browser request coming from
 * another site is rejected even if a browser ignored SameSite. Requests without an Origin header (curl,
 * Postman) carry no ambient cookie risk and are allowed.
 */
@Component
public class OriginValidator {

    private final Set<String> allowedOrigins;

    public OriginValidator(SecurityProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.allowedOrigins());
    }

    public void verify(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new BusinessException(ErrorCode.ORIGIN_NOT_ALLOWED);
        }
    }
}
