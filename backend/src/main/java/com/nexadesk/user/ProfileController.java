package com.nexadesk.user;

import com.nexadesk.auth.AuthService;
import com.nexadesk.auth.RefreshTokenCookies;
import com.nexadesk.auth.dto.AuthResponse;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.user.dto.ChangePasswordRequest;
import com.nexadesk.user.dto.ProfileDto;
import com.nexadesk.user.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profil", description = "Compte de l'utilisateur connecté")
public class ProfileController {

    private final ProfileService service;
    private final CurrentUser currentUser;
    private final RefreshTokenCookies cookies;

    @GetMapping
    @Operation(summary = "Mon profil")
    public ProfileDto get() {
        return service.get(currentUser.id());
    }

    @PutMapping
    @Operation(summary = "Modifier mon profil", description = "Poste, téléphone et couleur d'avatar.")
    public ProfileDto update(@Valid @RequestBody UpdateProfileRequest request) {
        return service.update(currentUser.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Changer mon mot de passe",
            description = "Met fin à toutes les autres sessions et renvoie de nouveaux jetons pour la session courante.")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthService.AuthResult result = service.changePassword(currentUser.id(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken().rawValue()).toString())
                .body(AuthResponse.bearer(result.accessToken().value(), result.accessToken().expiresAt(),
                        result.user()));
    }
}
