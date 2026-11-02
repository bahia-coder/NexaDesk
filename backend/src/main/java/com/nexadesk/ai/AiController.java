package com.nexadesk.ai;

import com.nexadesk.ai.dto.AiDtos.AiStatusDto;
import com.nexadesk.ai.dto.AiDtos.AssistantAnswerDto;
import com.nexadesk.ai.dto.AiDtos.AssistantRequest;
import com.nexadesk.ai.dto.AiDtos.ClassificationDto;
import com.nexadesk.ai.dto.AiDtos.SimilarDto;
import com.nexadesk.ai.dto.AiDtos.SuggestionsDto;
import com.nexadesk.ai.dto.AiDtos.SummaryDto;
import com.nexadesk.ai.dto.AiDtos.TextRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "IA", description = "Classification, résumé, suggestions, similarité et assistant. Chaque réponse "
        + "indique sa source : LLM (modèle connecté), HEURISTIC (règles locales) ou DISABLED.")
public class AiController {

    private final AiService service;

    @GetMapping("/status")
    @Operation(summary = "Mode d'IA actif", description = "Pour afficher le bon badge dans l'interface.")
    public AiStatusDto status() {
        return service.status();
    }

    @PostMapping("/classify")
    @Operation(summary = "Suggérer catégorie, impact et urgence",
            description = "La priorité découle de la matrice impact × urgence.")
    public ClassificationDto classify(@Valid @RequestBody TextRequest request) {
        return service.classify(request);
    }

    @PostMapping("/similar")
    @Operation(summary = "Tickets et articles similaires avant création",
            description = "Recherche PostgreSQL (toujours active). Un employé ne voit que ses propres tickets.")
    public SimilarDto similar(@Valid @RequestBody TextRequest request) {
        return service.similarToText(request);
    }

    @GetMapping("/tickets/{id}/similar")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Incidents similaires à un ticket", description = "Score de 1 à 100.")
    public SimilarDto similarToTicket(@PathVariable Long id, @RequestParam(defaultValue = "5") int limit) {
        return service.similarToTicket(id, limit);
    }

    @GetMapping("/tickets/{id}/summary")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Résumer l'historique d'un ticket")
    public SummaryDto summary(@PathVariable Long id) {
        return service.summary(id);
    }

    @GetMapping("/tickets/{id}/suggestions")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Pistes de résolution",
            description = "Solutions des tickets similaires résolus et articles pertinents ; un modèle connecté "
                    + "en tire des étapes.")
    public SuggestionsDto suggestions(@PathVariable Long id) {
        return service.suggestions(id);
    }

    @PostMapping("/assistant")
    @Operation(summary = "Assistant de la base de connaissances",
            description = "Répond à partir des articles publiés (RAG simple) ; sans modèle, renvoie les articles.")
    public AssistantAnswerDto assistant(@Valid @RequestBody AssistantRequest request) {
        return service.assistant(request);
    }
}
