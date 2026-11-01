package com.nexadesk.user;

import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.user.dto.TechnicianDtos.SpecialtiesRequest;
import com.nexadesk.user.dto.TechnicianDtos.TechnicianDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class TechnicianController {

    private final TechnicianService service;

    @GetMapping("/api/users/technicians")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Techniciens actifs", description = "Filtrables par spécialité (categoryId).")
    public List<TechnicianDto> technicians(@RequestParam(required = false) Long categoryId) {
        return service.technicians(categoryId);
    }

    @GetMapping("/api/users/{id}/specialties")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Spécialités d'un technicien")
    public List<CategoryRefDto> specialties(@PathVariable Long id) {
        return service.specialties(id);
    }

    @PutMapping("/api/users/{id}/specialties")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Définir les spécialités d'un technicien", description = "Remplace la liste complète.")
    public List<CategoryRefDto> replaceSpecialties(@PathVariable Long id, @Valid @RequestBody SpecialtiesRequest request) {
        return service.replaceSpecialties(id, request.categoryIds());
    }
}
