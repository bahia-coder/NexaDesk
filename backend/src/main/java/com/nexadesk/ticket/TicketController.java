package com.nexadesk.ticket;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.common.web.SortWhitelist;
import com.nexadesk.ticket.dto.TicketDtos.AssignRequest;
import com.nexadesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.nexadesk.ticket.dto.TicketDtos.ReasonRequest;
import com.nexadesk.ticket.dto.TicketDtos.ReclassifyRequest;
import com.nexadesk.ticket.dto.TicketDtos.StatusChangeRequest;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketFilter;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import com.nexadesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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

/**
 * Role checks here are coarse (who may call the endpoint at all); the fine-grained rules — ownership,
 * status, specialty — are enforced by {@link TicketPolicy} in the services.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Incidents et demandes de service")
public class TicketController {

    private static final Set<String> SORTABLE = Set.of(
            "createdAt", "updatedAt", "reference", "title", "status", "priority.rank", "resolutionDueAt");

    private final TicketService ticketService;
    private final TicketWorkflowService workflowService;

    @GetMapping
    @Operation(summary = "Lister les tickets visibles",
            description = "Filtres combinables (dont sla=AT_RISK|BREACHED) ; tri possible sur createdAt, updatedAt, "
                    + "reference, title, status, priority.rank, resolutionDueAt. Un employé ne voit que ses tickets, "
                    + "un technicien ceux de ses spécialités.")
    public PageResponse<TicketSummaryDto> list(@ParameterObject TicketFilter filter,
                                               @ParameterObject @PageableDefault(sort = "createdAt",
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return ticketService.list(filter, SortWhitelist.check(pageable, SORTABLE));
    }

    @GetMapping("/board")
    @Operation(summary = "Vue Kanban", description = "Tickets ouverts et résolus groupés par statut.")
    public Map<TicketStatus, List<TicketSummaryDto>> board(@ParameterObject TicketFilter filter) {
        return ticketService.board(filter);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un ticket", description = "Inclut les actions possibles pour l'utilisateur.")
    public TicketDetailDto get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @GetMapping("/by-reference/{reference}")
    @Operation(summary = "Détail d'un ticket par sa référence (ex. INC-2026-0042)")
    public TicketDetailDto getByReference(@PathVariable String reference) {
        return ticketService.getByReference(reference);
    }

    @PostMapping
    @Operation(summary = "Créer un ticket", description = "La priorité est calculée à partir de l'impact et de l'urgence.")
    public ResponseEntity<TicketDetailDto> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketDetailDto created = ticketService.create(request);
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier le titre et la description",
            description = "Le demandeur tant que le ticket est NEW ; le technicien assigné ou l'admin ensuite.")
    public TicketDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(id, request);
    }

    @PutMapping("/{id}/classification")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Changer la catégorie ou la priorité (motif obligatoire)")
    public TicketDetailDto reclassify(@PathVariable Long id, @Valid @RequestBody ReclassifyRequest request) {
        return ticketService.reclassify(id, request);
    }

    @PostMapping("/{id}/take")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Prendre en charge un ticket nouveau de sa spécialité")
    public TicketDetailDto take(@PathVariable Long id) {
        return workflowService.take(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Affecter ou réaffecter un ticket")
    public TicketDetailDto assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return workflowService.assign(id, request.technicianId(), request.comment());
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Changer le statut",
            description = "IN_PROGRESS (démarrer / reprendre), PENDING (pendingReason), RESOLVED (resolution), "
                    + "REJECTED (reason), CLOSED (admin).")
    public TicketDetailDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return workflowService.changeStatus(id, request);
    }

    @PostMapping("/{id}/confirm-resolution")
    @Operation(summary = "Confirmer la résolution (demandeur)")
    public TicketDetailDto confirmResolution(@PathVariable Long id) {
        return workflowService.confirmResolution(id);
    }

    @PostMapping("/{id}/reject-resolution")
    @Operation(summary = "Refuser la résolution (demandeur)")
    public TicketDetailDto rejectResolution(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return workflowService.rejectResolution(id, request.reason());
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Rouvrir un ticket fermé",
            description = "Le demandeur dans les 7 jours suivant la clôture ; l'admin sans limite.")
    public TicketDetailDto reopen(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return workflowService.reopen(id, request.reason());
    }
}
