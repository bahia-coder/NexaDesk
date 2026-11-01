package com.nexadesk.user;

import com.nexadesk.auth.AuthService;
import com.nexadesk.auth.RefreshTokenService;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.user.dto.ChangePasswordRequest;
import com.nexadesk.user.dto.ProfileDto;
import com.nexadesk.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The connected user's own account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthService authService;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public ProfileDto get(Long userId) {
        return mapper.toProfile(find(userId));
    }

    @Transactional
    public ProfileDto update(Long userId, UpdateProfileRequest request) {
        User user = find(userId);
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setPhone(blankToNull(request.phone()));
        if (request.avatarColor() != null) {
            user.setAvatarColor(request.avatarColor().toUpperCase());
        }
        return mapper.toProfile(user);
    }

    /**
     * Changes the password, ends every other session (a stolen session dies with the old password) and opens
     * a fresh one for the caller, returned so the user stays logged in on this device.
     */
    @Transactional
    public AuthService.AuthResult changePassword(Long userId, ChangePasswordRequest request) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        refreshTokenService.revokeAllForUser(userId);
        log.info("User {} changed their password", userId);
        return authService.startSession(user);
    }

    private User find(Long userId) {
        return userRepository.findWithRoleById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
