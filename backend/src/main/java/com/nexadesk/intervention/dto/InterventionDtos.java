package com.nexadesk.intervention.dto;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class InterventionDtos {

    private InterventionDtos() {
    }

    /** {@code editable}: whether the current user may still modify it (author or admin, ticket not closed). */
    public record InterventionDto(Long id, Long ticketId, String ticketReference, String ticketTitle,
                                  UserRefDto technician, String diagnosis, String solution, int timeSpentMinutes,
                                  Instant startedAt, Instant endedAt, Instant createdAt, Instant updatedAt,
                                  boolean editable) {
    }

    public record TicketInterventionsDto(List<InterventionDto> interventions, long totalMinutes) {
    }

    public record MyInterventionsDto(PageResponse<InterventionDto> page, long totalMinutes) {
    }

    public record InterventionRequest(
            @NotBlank(message = "Le diagnostic est obligatoire.") @Size(max = 5000) String diagnosis,
            @Size(max = 5000) String solution,
            @NotNull(message = "Le temps passé est obligatoire.")
            @Min(value = 1, message = "Le temps passé doit être d'au moins 1 minute.")
            @Max(value = 1440, message = "Une intervention ne peut pas dépasser 24 heures (1440 minutes).")
            Integer timeSpentMinutes,
            Instant startedAt,
            Instant endedAt) {
    }
}
