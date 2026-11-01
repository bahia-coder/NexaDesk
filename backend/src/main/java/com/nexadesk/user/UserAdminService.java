package com.nexadesk.user;

import com.nexadesk.auth.RefreshTokenService;
import com.nexadesk.common.web.PageResponse;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.user.dto.CreateUserRequest;
import com.nexadesk.user.dto.DepartmentDto;
import com.nexadesk.user.dto.TemporaryPasswordResponse;
import com.nexadesk.user.dto.UpdateUserRequest;
import com.nexadesk.user.dto.UserDto;
import com.nexadesk.user.dto.UserFilter;
import com.nexadesk.user.password.TemporaryPasswordGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account administration (ADMIN only — enforced on the controller). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(UserFilter filter, Pageable pageable) {
        return PageResponse.from(userRepository.findAll(UserSpecifications.matching(filter), pageable)
                .map(mapper::toDto));
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return mapper.toDto(find(id));
    }

    @Transactional
    public TemporaryPasswordResponse create(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        String temporaryPassword = passwordGenerator.generate();
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .jobTitle(blankToNull(request.jobTitle()))
                .phone(blankToNull(request.phone()))
                .role(role(request.role()))
                .department(department(request.departmentId()))
                .build();
        user.setMustChangePassword(true);
        userRepository.save(user);
        log.info("User {} created with role {}", user.getId(), request.role());
        return new TemporaryPasswordResponse(mapper.toDto(user), temporaryPassword);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request) {
        User user = find(id);
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setPhone(blankToNull(request.phone()));
        user.setDepartment(department(request.departmentId()));
        return mapper.toDto(user);
    }

    @Transactional
    public UserDto changeRole(Long id, RoleCode newRole, Long actorId) {
        if (id.equals(actorId)) {
            throw new BusinessException(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        }
        User user = find(id);
        if (user.getRoleCode() == newRole) {
            return mapper.toDto(user);
        }
        ensureNotLastActiveAdmin(user);
        user.setRole(role(newRole));
        if (newRole != RoleCode.TECHNICIAN) {
            user.replaceSpecialties(List.of());
        }
        // The role is embedded in issued access tokens: end existing sessions so it takes effect promptly.
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("User {} role changed to {} by {}", id, newRole, actorId);
        return mapper.toDto(user);
    }

    @Transactional
    public UserDto deactivate(Long id, Long actorId) {
        if (id.equals(actorId)) {
            throw new BusinessException(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        }
        User user = find(id);
        if (!user.isActive()) {
            return mapper.toDto(user);
        }
        ensureNotLastActiveAdmin(user);
        user.setActive(false);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("User {} deactivated by {}", id, actorId);
        return mapper.toDto(user);
    }

    @Transactional
    public UserDto activate(Long id) {
        User user = find(id);
        user.setActive(true);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        return mapper.toDto(user);
    }

    /** Also the way to unlock an account whose owner forgot the password. */
    @Transactional
    public TemporaryPasswordResponse resetPassword(Long id) {
        User user = find(id);
        String temporaryPassword = passwordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password of user {} reset by an administrator", id);
        return new TemporaryPasswordResponse(mapper.toDto(user), temporaryPassword);
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> departments() {
        return departmentRepository.findAll(Sort.by("name")).stream().map(mapper::toDto).toList();
    }

    private void ensureNotLastActiveAdmin(User user) {
        if (user.getRoleCode() == RoleCode.ADMIN && user.isActive()
                && userRepository.countByRole_CodeAndActiveTrue(RoleCode.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        }
    }

    private User find(Long id) {
        return userRepository.findWithRoleById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }

    private Role role(RoleCode code) {
        return roleRepository.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("Rôle", code));
    }

    private Department department(Long id) {
        if (id == null) {
            return null;
        }
        return departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Département", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
