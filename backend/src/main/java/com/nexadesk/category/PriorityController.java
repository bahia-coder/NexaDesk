package com.nexadesk.category;

import com.nexadesk.category.dto.CategoryDtos.MatrixEntryDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityDto;
import com.nexadesk.category.dto.CategoryDtos.PriorityRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/priorities")
@RequiredArgsConstructor
@Tag(name = "Priorités", description = "Niveaux de priorité et matrice impact × urgence")
public class PriorityController {

    private final PriorityService service;

    @GetMapping
    @Operation(summary = "Lister les priorités (de la plus basse à la plus haute)")
    public List<PriorityDto> list() {
        return service.list();
    }

    @GetMapping("/matrix")
    @Operation(summary = "Matrice impact × urgence → priorité",
            description = "Permet au formulaire de création d'afficher la priorité calculée avant l'envoi.")
    public List<MatrixEntryDto> matrix() {
        return service.matrix();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier le libellé et la couleur d'une priorité")
    public PriorityDto update(@PathVariable Long id, @Valid @RequestBody PriorityRequest request) {
        return service.update(id, request);
    }
}
