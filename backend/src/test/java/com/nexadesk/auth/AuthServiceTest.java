package com.nexadesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.security.JwtService;
import com.nexadesk.support.TestSecurityProperties;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserMapper;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final String EMAIL = "employe@nexadesk.test";
    private static final String PASSWORD = "Secret@123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;

    // Real encoder (low cost factor for speed): the service's behaviour depends on actual hash comparison.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, userMapper,
                TestSecurityProperties.create(), Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestUsers.user(42L, EMAIL, RoleCode.EMPLOYEE, passwordEncoder.encode(PASSWORD));
    }

    @Test
    void loginWithValidCredentialsIssuesTokensAndResetsCounters() {
        user.setFailedLoginAttempts(3);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new JwtService.AccessToken("jwt", NOW.plusSeconds(900)));
        when(refreshTokenService.issue(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh", NOW.plusSeconds(3600)));

        AuthService.AuthResult result = authService.login("  Employe@NexaDesk.test ", PASSWORD);

        assertThat(result.accessToken().value()).isEqualTo("jwt");
        assertThat(result.refreshToken().rawValue()).isEqualTo("refresh");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void unknownEmailIsRejectedWithGenericError() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@nexadesk.test", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void wrongPasswordIncrementsFailedAttempts() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void reachingMaxAttemptsLocksTheAccount() {
        user.setFailedLoginAttempts(TestSecurityProperties.MAX_ATTEMPTS - 1);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(TestSecurityProperties.LOCK_DURATION));
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheRightPassword() {
        user.setLockedUntil(NOW.plusSeconds(60));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        verify(jwtService, never()).issue(any());
    }

    @Test
    void expiredLockNoLongerBlocksLogin() {
        user.setLockedUntil(NOW.minusSeconds(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new JwtService.AccessToken("jwt", NOW.plusSeconds(900)));
        when(refreshTokenService.issue(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh", NOW.plusSeconds(3600)));

        authService.login(EMAIL, PASSWORD);

        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void disabledAccountIsRejected() {
        user.setActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void currentUserRejectsDeactivatedAccount() {
        user.setActive(false);
        when(userRepository.findWithRoleById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.currentUser(42L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
