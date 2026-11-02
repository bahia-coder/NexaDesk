package com.nexadesk.search;

import com.nexadesk.search.GlobalSearchService.GlobalSearchDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Recherche", description = "Recherche globale du header")
public class GlobalSearchController {

    private final GlobalSearchService service;

    @GetMapping
    @Operation(summary = "Rechercher partout",
            description = "Tickets (référence ou texte) et articles ; pour l'admin, aussi utilisateurs et "
                    + "équipements. 5 résultats par type.")
    public GlobalSearchDto search(@RequestParam @NotBlank @Size(min = 2, max = 100) String q) {
        return service.search(q);
    }
}
