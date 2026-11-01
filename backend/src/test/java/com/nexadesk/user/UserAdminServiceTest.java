package com.nexadesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.auth.RefreshTokenService;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.dto.CreateUserRequest;
import com.nexadesk.user.dto.TemporaryPasswordResponse;
import com.nexadesk.user.dto.UpdateUserRequest;
import com.nexadesk.user.password.StrongPasswordValidator;
import com.nexadesk.user.password.TemporaryPasswordGenerator;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final long ADMIN_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TicketRepository ticketRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, roleRepository, departmentRepository, refreshTokenService,
                ticketRepository, passwordEncoder, new TemporaryPasswordGenerator(), new UserMapperImpl());
    }

    @Test
    void technicianWithOpenTicketsCannotBeDeactivatedNorDemoted() {
        existing(8L, RoleCode.TECHNICIAN);
        Ticket open = Ticket.builder().reference("INC-2026-0007").build();
        when(ticketRepository.findTop10ByAssignee_IdAndStatusInOrderByCreatedAtAsc(8L, TicketStatus.OPEN))
                .thenReturn(List.of(open));

        assertThatThrownBy(() -> service.deactivate(8L, ADMIN_ID))
                .hasMessageContaining("INC-2026-0007")
                .extracting("code").isEqualTo(ErrorCode.TECHNICIAN_HAS_OPEN_TICKETS);
        assertThatThrownBy(() -> service.changeRole(8L, RoleCode.EMPLOYEE, ADMIN_ID))
                .extracting("code").isEqualTo(ErrorCode.TECHNICIAN_HAS_OPEN_TICKETS);
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    private User existing(long id, RoleCode role) {
        User user = TestUsers.user(id, "user" + id + "@nexadesk.test", role, "hash");
        when(userRepository.findWithRoleById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void createRejectsAnEmailAlreadyInUse() {
        when(userRepository.existsByEmail("taken@nexadesk.test")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest(" Taken@NexaDesk.test", "A", "B", null, null,
                RoleCode.EMPLOYEE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.EMAIL_ALREADY_USED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createIssuesAStrongTemporaryPasswordThatMustBeChanged() {
        when(roleRepository.findByCode(RoleCode.TECHNICIAN)).thenReturn(Optional.of(TestUsers.role(RoleCode.TECHNICIAN)));

        TemporaryPasswordResponse response = service.create(new CreateUserRequest("new@nexadesk.test", " Lina ",
                "Haddad", "  ", null, RoleCode.TECHNICIAN, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getFirstName()).isEqualTo("Lina");
        assertThat(user.getJobTitle()).isNull();
        assertThat(StrongPasswordValidator.isStrong(response.temporaryPassword())).isTrue();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), user.getPasswordHash())).isTrue();
        assertThat(response.user().role()).isEqualTo(RoleCode.TECHNICIAN);
    }

    @Test
    void updateRejectsAnEmailUsedByAnotherAccount() {
        existing(5L, RoleCode.EMPLOYEE);
        when(userRepository.existsByEmailAndIdNot("other@nexadesk.test", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, new UpdateUserRequest("other@nexadesk.test", "A", "B", null,
                null, null)))
                .extracting("code").isEqualTo(ErrorCode.EMAIL_ALREADY_USED);
    }

    @Test
    void adminCannotChangeTheirOwnRole() {
        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, RoleCode.EMPLOYEE, ADMIN_ID))
                .extracting("code").isEqualTo(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
    }

    @Test
    void lastActiveAdminCannotBeDemoted() {
        existing(2L, RoleCode.ADMIN);
        when(userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(2L, RoleCode.TECHNICIAN, ADMIN_ID))
                .extracting("code").isEqualTo(ErrorCode.LAST_ADMIN);
    }

    @Test
    void changingARoleEndsTheUserSessions() {
        User user = existing(3L, RoleCode.EMPLOYEE);
        when(roleRepository.findByCode(RoleCode.TECHNICIAN)).thenReturn(Optional.of(TestUsers.role(RoleCode.TECHNICIAN)));

        service.changeRole(3L, RoleCode.TECHNICIAN, ADMIN_ID);

        assertThat(user.getRoleCode()).isEqualTo(RoleCode.TECHNICIAN);
        verify(refreshTokenService).revokeAllForUser(3L);
    }

    @Test
    void adminCannotDeactivateThemself() {
        assertThatThrownBy(() -> service.deactivate(ADMIN_ID, ADMIN_ID))
                .extracting("code").isEqualTo(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void deactivationRevokesAllSessions() {
        User user = existing(4L, RoleCode.EMPLOYEE);

        service.deactivate(4L, ADMIN_ID);

        assertThat(user.isActive()).isFalse();
        verify(refreshTokenService).revokeAllForUser(4L);
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        existing(2L, RoleCode.ADMIN);
        when(userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.deactivate(2L, ADMIN_ID))
                .extracting("code").isEqualTo(ErrorCode.LAST_ADMIN);
    }

    @Test
    void resetPasswordUnlocksTheAccountAndForcesAChange() {
        User user = existing(6L, RoleCode.EMPLOYEE);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        user.setFailedLoginAttempts(3);

        TemporaryPasswordResponse response = service.resetPassword(6L);

        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), user.getPasswordHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(6L);
    }

    @Test
    void unknownUserIsReportedAsNotFound() {
        when(userRepository.findWithRoleById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
