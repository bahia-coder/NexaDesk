package com.nexadesk.auth;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.security.SecurityProperties;
import com.nexadesk.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Opaque, rotating refresh tokens. The raw value only ever exists in the client's httpOnly cookie;
 * the database stores its SHA-256 hash.
 *
 * <p>Not transactional on purpose: it always runs inside the caller's transaction ({@link AuthService}),
 * which must commit revocations even when it then rejects the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public record IssuedRefreshToken(String rawValue, Instant expiresAt) {
    }

    public record Rotation(User user, IssuedRefreshToken token) {
    }

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Starts a new token family (one per login). */
    public IssuedRefreshToken issue(User user) {
        return create(user, UUID.randomUUID(), clock.instant());
    }

    /**
     * Exchanges a valid refresh token for a new one of the same family. Presenting an already-rotated token
     * after the grace period means it was probably stolen: the whole family is revoked.
     */
    public Rotation rotate(String rawValue) {
        RefreshToken current = find(rawValue);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            if (current.getRevokedAt().plus(properties.refreshToken().reuseGrace()).isBefore(now)) {
                log.warn("Refresh token reuse detected for user {}: revoking token family {}",
                        current.getUser().getId(), current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now);
            }
            throw invalid();
        }
        if (current.isExpiredAt(now)) {
            throw invalid();
        }
        User user = current.getUser();
        if (!user.isActive()) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }

        current.revoke(now);
        return new Rotation(user, create(user, current.getFamilyId(), now));
    }

    /** Logout: revokes every token of the session. Unknown tokens are ignored (logout is idempotent). */
    public void revokeFamily(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawValue))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Ends every session of a user (deactivation, password change). */
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private IssuedRefreshToken create(User user, UUID familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.refreshToken().ttl());
        repository.save(new RefreshToken(user, hash(raw), familyId, now, expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    private RefreshToken find(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw invalid();
        }
        return repository.findByTokenHash(hash(rawValue)).orElseThrow(RefreshTokenService::invalid);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    static String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
