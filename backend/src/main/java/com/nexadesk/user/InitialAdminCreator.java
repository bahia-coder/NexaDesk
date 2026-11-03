package com.nexadesk.user;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.user.password.StrongPasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First start of a real installation (without the demo data): creates the administrator named in the environment
 * (BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD), so that someone can sign in and create the other accounts.
 *
 * <ul>
 *   <li>Does nothing when no e-mail is configured, or once an active administrator exists.</li>
 *   <li>The password is temporary: it must be changed at the first sign-in.</li>
 *   <li>A weak password stops the start-up: a misconfiguration must not go unnoticed.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(0)
public class InitialAdminCreator implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public InitialAdminCreator(UserRepository userRepository, RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder, AuditService audit,
                               @Value("${nexadesk.bootstrap.admin-email:}") String email,
                               @Value("${nexadesk.bootstrap.admin-password:}") String password,
                               @Value("${nexadesk.bootstrap.admin-first-name:Administrateur}") String firstName,
                               @Value("${nexadesk.bootstrap.admin-last-name:NexaDesk}") String lastName) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            return;
        }
        if (userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN) > 0) {
            log.info("An administrator already exists: BOOTSTRAP_ADMIN_EMAIL is ignored");
            return;
        }
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            log.warn("BOOTSTRAP_ADMIN_EMAIL {} belongs to an existing account: no administrator created", normalized);
            return;
        }
        if (!StrongPasswordValidator.isStrong(password)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must have at least "
                    + StrongPasswordValidator.MIN_LENGTH + " characters with an upper case letter, a lower case "
                    + "letter, a digit and a special character");
        }
        User admin = User.builder()
                .email(normalized)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .role(roleRepository.findByCode(RoleCode.ADMIN).orElseThrow())
                .build();
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        audit.recordFor(null, null, AuditAction.USER_CREATED, "USER", admin.getId(),
                "Création du premier administrateur " + normalized + " (installation)");
        log.info("First administrator {} created: the password must be changed at the first sign-in", normalized);
    }
}
