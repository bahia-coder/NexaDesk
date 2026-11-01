package com.nexadesk.intervention;

import com.nexadesk.common.web.SortWhitelist;
import com.nexadesk.intervention.dto.InterventionDtos.InterventionDto;
import com.nexadesk.intervention.dto.InterventionDtos.InterventionRequest;
import com.nexadesk.intervention.dto.InterventionDtos.MyInterventionsDto;
import com.nexadesk.intervention.dto.InterventionDtos.TicketInterventionsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Interventions", description = "Journal de travail des techniciens (diagnostic, solution, temps passé)")
public class InterventionController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "timeSpentMinutes");

    private final InterventionService service;

    @GetMapping("/api/tickets/{ticketId}/interventions")
    @Operation(summary = "Interventions d'un ticket", description = "Avec le temps total passé.")
    public TicketInterventionsDto list(@PathVariable Long ticketId) {
        return service.list(ticketId);
    }

    @PostMapping("/api/tickets/{ticketId}/interventions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une intervention",
            description = "Technicien assigné ou admin, ticket en cours ou en attente.")
    public InterventionDto add(@PathVariable Long ticketId, @Valid @RequestBody InterventionRequest request) {
        return service.add(ticketId, request);
    }

    @PutMapping("/api/tickets/{ticketId}/interventions/{interventionId}")
    @Operation(summary = "Modifier une intervention", description = "Auteur ou admin, tant que le ticket n'est pas fermé.")
    public InterventionDto update(@PathVariable Long ticketId, @PathVariable Long interventionId,
                                  @Valid @RequestBody InterventionRequest request) {
        return service.update(ticketId, interventionId, request);
    }

    @GetMapping("/api/interventions/mine")
    @Operation(summary = "Mon historique d'interventions",
            description = "Période par défaut : les 30 derniers jours. Inclut le temps total sur la période.")
    public MyInterventionsDto mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.mine(from, to, SortWhitelist.check(pageable, SORTABLE));
    }
}
