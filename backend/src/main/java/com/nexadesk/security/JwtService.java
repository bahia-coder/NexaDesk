package com.nexadesk.security;

import com.nexadesk.user.User;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens. Subject = user id; role, e-mail and name are informational claims. */
@Service
@RequiredArgsConstructor
public class JwtService {

    public record AccessToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim(JwtConfig.ROLE_CLAIM, user.getRoleCode().name())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName());
        if (user.isMustChangePassword()) {
            claims.claim(JwtConfig.PASSWORD_CHANGE_CLAIM, true);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
