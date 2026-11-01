package com.nexadesk.user;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.common.web.SortWhitelist;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.user.dto.ChangeRoleRequest;
import com.nexadesk.user.dto.CreateUserRequest;
import com.nexadesk.user.dto.TemporaryPasswordResponse;
import com.nexadesk.user.dto.UpdateUserRequest;
import com.nexadesk.user.dto.UserDto;
import com.nexadesk.user.dto.UserFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Administration des comptes (administrateur)")
public class UserAdminController {

    private static final Set<String> SORTABLE = Set.of(
            "lastName", "firstName", "email", "createdAt", "lastLoginAt", "active");

    private final UserAdminService service;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "Lister les utilisateurs",
            description = "Filtres combinables : search (nom, prénom, e-mail), role, active, departmentId.")
    public PageResponse<UserDto> list(@ParameterObject UserFilter filter,
                                      @ParameterObject @PageableDefault(sort = "lastName", direction = Sort.Direction.ASC)
                                      Pageable pageable) {
        return service.list(filter, SortWhitelist.check(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un utilisateur")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur",
            description = "Génère un mot de passe temporaire, renvoyé une seule fois, à changer à la première connexion.")
    public ResponseEntity<TemporaryPasswordResponse> create(@Valid @RequestBody CreateUserRequest request) {
        TemporaryPasswordResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.user().id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier les informations d'un utilisateur")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Changer le rôle", description = "Impossible sur son propre compte ; met fin aux sessions.")
    public UserDto changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(id, request.role(), currentUser.id());
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Désactiver un compte", description = "Le compte ne peut plus se connecter ; ses sessions sont révoquées.")
    public UserDto deactivate(@PathVariable Long id) {
        return service.deactivate(id, currentUser.id());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Réactiver un compte", description = "Lève aussi un éventuel verrouillage.")
    public UserDto activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe",
            description = "Génère un mot de passe temporaire, déverrouille le compte et met fin à ses sessions.")
    public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
        return service.resetPassword(id);
    }
}
