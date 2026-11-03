package com.nexadesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.audit.AuditService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminCreatorTest {

    private static final String PASSWORD = "Install@2026!";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService audit;

    private InitialAdminCreator creator(String email, String password) {
        return new InitialAdminCreator(userRepository, roleRepository, passwordEncoder, audit, email, password,
                "Amina", "Berrada");
    }

    @Test
    void createsTheFirstAdministratorWithATemporaryPassword() {
        when(userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN)).thenReturn(0L);
        Role adminRole = mock(Role.class);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");

        creator(" Admin@Entreprise.fr ", PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@entreprise.fr");
        assertThat(saved.getValue().getRole()).isSameAs(adminRole);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
    }

    @Test
    void doesNothingWithoutEmailOrOnceAnAdministratorExists() {
        creator("", PASSWORD).run(null);
        when(userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN)).thenReturn(1L);
        creator("admin@entreprise.fr", PASSWORD).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesToStartWithAWeakPassword() {
        when(userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN)).thenReturn(0L);

        assertThatThrownBy(() -> creator("admin@entreprise.fr", "admin").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
        verify(userRepository, never()).save(any());
    }
}
