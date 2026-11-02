package com.nexadesk.audit;

import com.nexadesk.audit.dto.AuditDtos.AuditFilter;
import com.nexadesk.audit.dto.AuditDtos.AuditLogDto;
import com.nexadesk.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Journal de sécurité et d'administration (administrateur)")
public class AuditController {

    private final AuditQueryService service;

    @GetMapping
    @Operation(summary = "Rechercher dans le journal d'audit",
            description = "Filtres : actorId, action, entityType (USER, TICKET, EQUIPMENT…), entityId, from, to.")
    public PageResponse<AuditLogDto> search(@ParameterObject AuditFilter filter,
                                            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return service.search(filter, pageable);
    }

    @GetMapping("/actions")
    @Operation(summary = "Liste des actions auditées (pour les filtres)")
    public List<AuditAction> actions() {
        return List.of(AuditAction.values());
    }
}
