package com.nexadesk.auth;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.security.JwtService;
import com.nexadesk.security.SecurityProperties;
import com.nexadesk.user.User;
import com.nexadesk.user.UserMapper;
import com.nexadesk.user.UserRepository;
import com.nexadesk.user.dto.CurrentUserDto;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    public record AuthResult(JwtService.AccessToken accessToken,
                             RefreshTokenService.IssuedRefreshToken refreshToken,
                             CurrentUserDto user) {
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final SecurityProperties properties;
    private final Clock clock;

    /** Compared against when the e-mail is unknown, so response time does not reveal which accounts exist. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, UserMapper userMapper,
                       SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("nexadesk-timing-equalizer");
    }

    /**
     * {@code noRollbackFor}: a failed attempt must still persist the incremented counter / the lock,
     * even though the method then throws.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult login(String email, String password) {
        Instant now = clock.instant();
        User user = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLockedAt(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            SecurityProperties.Lockout lockout = properties.lockout();
            if (user.registerFailedLogin(lockout.maxAttempts(), now.plus(lockout.duration()))) {
                log.warn("Account {} locked after {} failed login attempts", user.getId(), lockout.maxAttempts());
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.registerSuccessfulLogin(now);
        return startSession(user);
    }

    /** Opens a new session (token family) for an already authenticated user. */
    @Transactional
    public AuthResult startSession(User user) {
        return new AuthResult(jwtService.issue(user), refreshTokenService.issue(user), userMapper.toCurrentUser(user));
    }

    /** {@code noRollbackFor}: a family revoked because of token reuse must stay revoked. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = rotation.user();
        return new AuthResult(jwtService.issue(user), rotation.token(), userMapper.toCurrentUser(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamily(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public CurrentUserDto currentUser(Long userId) {
        return userRepository.findWithRoleById(userId)
                .filter(User::isActive)
                .map(userMapper::toCurrentUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
