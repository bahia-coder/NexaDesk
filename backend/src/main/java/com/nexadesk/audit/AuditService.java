package com.nexadesk.audit;

import com.nexadesk.common.web.TraceIdFilter;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit entries in the caller's transaction: a change that is rolled back leaves no audit trace, while
 * failed logins (committed on purpose, see AuthService) are recorded.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** Entry attributed to the authenticated user (or to the system when there is none). */
    public void record(AuditAction action, String entityType, Object entityId, String summary,
                       Map<String, Object> oldValue, Map<String, Object> newValue) {
        Jwt jwt = currentJwt();
        User actor = jwt == null ? null : userRepository.getReferenceById(Long.valueOf(jwt.getSubject()));
        String email = jwt == null ? null : jwt.getClaimAsString("email");
        save(actor, email, action, entityType, entityId, summary, oldValue, newValue);
    }

    public void record(AuditAction action, String entityType, Object entityId, String summary) {
        record(action, entityType, entityId, summary, null, null);
    }

    /** Entry with an explicit actor: logins happen before any authentication exists. */
    public void recordFor(User actor, String email, AuditAction action, String entityType, Object entityId,
                          String summary) {
        save(actor, email, action, entityType, entityId, summary, null, null);
    }

    /** Builds a value map that, unlike {@code Map.of}, accepts null values. */
    public static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1] instanceof Enum<?> e
                    ? e.name() : keysAndValues[i + 1]);
        }
        return map;
    }

    private void save(User actor, String email, AuditAction action, String entityType, Object entityId,
                      String summary, Map<String, Object> oldValue, Map<String, Object> newValue) {
        repository.save(AuditLog.builder()
                .actor(actor)
                .actorEmail(email)
                .action(action)
                .entityType(entityType)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .summary(summary)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(clientIp())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY))
                .createdAt(clock.instant())
                .build());
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }

    /** Behind Nginx, the client address comes from X-Forwarded-For (server.forward-headers-strategy). */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
