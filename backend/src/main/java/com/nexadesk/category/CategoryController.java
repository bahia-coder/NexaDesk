package com.nexadesk.category;

import com.nexadesk.category.dto.CategoryDtos.CategoryDto;
import com.nexadesk.category.dto.CategoryDtos.CategoryRequest;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryDto;
import com.nexadesk.category.dto.CategoryDtos.SubCategoryRequest;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.user.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Catégories", description = "Catégories et sous-catégories de tickets")
public class CategoryController {

    private final CategoryService service;
    private final CurrentUser currentUser;

    @GetMapping("/categories")
    @Operation(summary = "Lister les catégories et leurs sous-catégories",
            description = "includeInactive=true n'est pris en compte que pour un administrateur.")
    public List<CategoryDto> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive && currentUser.hasRole(RoleCode.ADMIN));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une catégorie")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryRequest request) {
        CategoryDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + created.id())).body(created);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier une catégorie")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/categories/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver une catégorie")
    public CategoryDto deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/categories/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réactiver une catégorie")
    public CategoryDto activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/categories/{id}/subcategories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter une sous-catégorie")
    public ResponseEntity<SubCategoryDto> addSubcategory(@PathVariable Long id,
                                                         @Valid @RequestBody SubCategoryRequest request) {
        SubCategoryDto created = service.addSubcategory(id, request);
        return ResponseEntity.created(URI.create("/api/subcategories/" + created.id())).body(created);
    }

    @PutMapping("/subcategories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Renommer une sous-catégorie")
    public SubCategoryDto renameSubcategory(@PathVariable Long id, @Valid @RequestBody SubCategoryRequest request) {
        return service.renameSubcategory(id, request);
    }

    @PostMapping("/subcategories/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver une sous-catégorie")
    public SubCategoryDto deactivateSubcategory(@PathVariable Long id) {
        return service.setSubcategoryActive(id, false);
    }

    @PostMapping("/subcategories/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réactiver une sous-catégorie")
    public SubCategoryDto activateSubcategory(@PathVariable Long id) {
        return service.setSubcategoryActive(id, true);
    }
}
