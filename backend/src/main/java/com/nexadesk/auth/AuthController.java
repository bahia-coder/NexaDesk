package com.nexadesk.auth;

import com.nexadesk.auth.dto.AuthResponse;
import com.nexadesk.auth.dto.LoginRequest;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.security.OriginValidator;
import com.nexadesk.user.dto.CurrentUserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion, renouvellement de session et déconnexion")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;
    private final OriginValidator originValidator;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Se connecter",
            description = "Renvoie un access token (Bearer) et pose le refresh token dans un cookie httpOnly.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renouveler l'access token",
            description = "Utilise le cookie de refresh, le remplace par un nouveau (rotation).")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        originValidator.verify(request);
        return withRefreshCookie(authService.refresh(cookies.read(request)));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Se déconnecter", description = "Révoque la session et efface le cookie.")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        originValidator.verify(request);
        authService.logout(cookies.read(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Utilisateur connecté")
    public CurrentUserDto me() {
        return authService.currentUser(currentUser.id());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken().rawValue()).toString())
                .body(AuthResponse.bearer(result.accessToken().value(), result.accessToken().expiresAt(),
                        result.user()));
    }
}
