package com.nexadesk.equipment;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.common.web.SortWhitelist;
import com.nexadesk.equipment.dto.EquipmentDtos.AssignRequest;
import com.nexadesk.equipment.dto.EquipmentDtos.AssignmentDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentFilter;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentRefDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentRequest;
import com.nexadesk.equipment.dto.EquipmentDtos.StatusRequest;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@Tag(name = "Équipements", description = "Inventaire du matériel informatique")
public class EquipmentController {

    private static final Set<String> SORTABLE = Set.of(
            "inventoryCode", "name", "type", "status", "acquisitionDate", "warrantyEndDate");

    private final EquipmentService service;

    @GetMapping
    @Operation(summary = "Lister les équipements",
            description = "Filtres : search (code, nom, marque, modèle, n° de série, emplacement), type, status, "
                    + "assignedUserId. Un employé ne voit que ses équipements.")
    public PageResponse<EquipmentDto> list(@ParameterObject EquipmentFilter filter,
                                           @ParameterObject @PageableDefault(sort = "inventoryCode",
                                                   direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(filter, SortWhitelist.check(pageable, SORTABLE));
    }

    @GetMapping("/selectable")
    @Operation(summary = "Équipements que je peux lier à un ticket",
            description = "Employé : ses équipements et le matériel partagé. Technicien / admin : tout le parc actif.")
    public List<EquipmentRefDto> selectable() {
        return service.selectable();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un équipement")
    public EquipmentDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter un équipement", description = "Le code d'inventaire (EQ-0001…) est généré.")
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentRequest request) {
        EquipmentDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/equipment/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier un équipement")
    public EquipmentDto update(@PathVariable Long id, @Valid @RequestBody EquipmentRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Affecter à un utilisateur", description = "userId null : retour en stock. Historisé.")
    public EquipmentDto assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return service.assign(id, request.userId());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Changer l'état", description = "IN_REPAIR, IN_USE (partagé : emplacement requis), IN_STOCK.")
    public EquipmentDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mettre au rebut (définitif)")
    public EquipmentDto retire(@PathVariable Long id) {
        return service.retire(id);
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Historique des affectations")
    public List<AssignmentDto> assignments(@PathVariable Long id) {
        return service.assignments(id);
    }

    @GetMapping("/{id}/tickets")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Historique des incidents de l'équipement")
    public List<TicketSummaryDto> tickets(@PathVariable Long id) {
        return service.tickets(id);
    }
}
