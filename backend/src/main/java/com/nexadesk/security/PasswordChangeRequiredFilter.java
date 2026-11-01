package com.nexadesk.security;

import com.nexadesk.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A user holding an admin-issued temporary password may only reach their profile (to change it) and the
 * auth endpoints. Enforced server-side: hiding screens in Angular alone would not protect the API.
 *
 * <p>Not a Spring bean on purpose: as a bean it would also be registered as a plain servlet filter,
 * outside the security chain.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String[] ALLOWED_PREFIXES = {"/api/auth/", "/api/profile"};

    private final ProblemDetailSecurityHandler problemWriter;

    public PasswordChangeRequiredFilter(ProblemDetailSecurityHandler problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (mustChangePassword() && !isAllowed(request.getRequestURI())) {
            problemWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtConfig.PASSWORD_CHANGE_CLAIM));
    }

    private static boolean isAllowed(String uri) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
