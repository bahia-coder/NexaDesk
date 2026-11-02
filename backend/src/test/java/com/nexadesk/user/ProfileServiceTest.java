package com.nexadesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.audit.AuditService;
import com.nexadesk.auth.AuthService;
import com.nexadesk.auth.RefreshTokenService;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.dto.ChangePasswordRequest;
import com.nexadesk.user.dto.UpdateProfileRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final long USER_ID = 10L;
    private static final String CURRENT = "Current@Pass1";
    private static final String NEW = "Brand#NewPass2";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthService authService;
    @Mock
    private AuditService audit;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private ProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userRepository, passwordEncoder, refreshTokenService, authService,
                new UserMapperImpl(), audit);
        user = TestUsers.user(USER_ID, "me@nexadesk.test", RoleCode.EMPLOYEE, passwordEncoder.encode(CURRENT));
        when(userRepository.findWithRoleById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(USER_ID, new ChangePasswordRequest("nope", NEW)))
                .extracting("code").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void reusingTheSamePasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(USER_ID, new ChangePasswordRequest(CURRENT, CURRENT)))
                .extracting("code").isEqualTo(ErrorCode.PASSWORD_UNCHANGED);
    }

    @Test
    void changingThePasswordEndsOtherSessionsAndOpensANewOne() {
        user.setMustChangePassword(true);

        service.changePassword(USER_ID, new ChangePasswordRequest(CURRENT, NEW));

        assertThat(passwordEncoder.matches(NEW, user.getPasswordHash())).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(authService).startSession(user);
    }

    @Test
    void profileUpdateOnlyTouchesSelfServiceFields() {
        service.update(USER_ID, new UpdateProfileRequest("Analyste", "+33 6 00 00 00 00", "#4f46e5"));

        assertThat(user.getJobTitle()).isEqualTo("Analyste");
        assertThat(user.getAvatarColor()).isEqualTo("#4F46E5");
        assertThat(user.getRoleCode()).isEqualTo(RoleCode.EMPLOYEE);
    }
}
