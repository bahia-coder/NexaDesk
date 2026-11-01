package com.nexadesk.sla.dto;

import com.nexadesk.category.PriorityCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public final class SlaDtos {

    private SlaDtos() {
    }

    public record SlaRuleDto(Long id, PriorityCode priority, String priorityLabel, String priorityColor,
                             int firstResponseMinutes, int resolutionMinutes, boolean businessHoursOnly,
                             int warningThresholdPercent) {
    }

    public record SlaRuleRequest(
            @NotNull @Min(1) @Max(value = 10_080, message = "Première réponse : 7 jours maximum.")
            Integer firstResponseMinutes,
            @NotNull @Min(1) @Max(value = 43_200, message = "Résolution : 30 jours maximum.")
            Integer resolutionMinutes,
            boolean businessHoursOnly,
            @NotNull @Min(value = 50, message = "Le seuil d'alerte va de 50 à 95 %.")
            @Max(value = 95, message = "Le seuil d'alerte va de 50 à 95 %.")
            Integer warningThresholdPercent) {
    }

    /** {@code timeZone} is read-only: it is deployment configuration (APP_TIMEZONE). */
    public record CalendarDto(String timeZone, LocalTime workStart, LocalTime workEnd, Set<DayOfWeek> workingDays) {
    }

    public record CalendarRequest(
            @NotNull(message = "L'heure de début est obligatoire.") LocalTime workStart,
            @NotNull(message = "L'heure de fin est obligatoire.") LocalTime workEnd,
            @NotEmpty(message = "Au moins un jour ouvré est requis.") Set<DayOfWeek> workingDays) {
    }

    public record HolidayDto(Long id, LocalDate day, String label) {
    }

    public record HolidayRequest(@NotNull(message = "La date est obligatoire.") LocalDate day,
                                 @NotBlank(message = "Le libellé est obligatoire.") @Size(max = 100) String label) {
    }

    public record SlaPreviewDto(PriorityCode priority, Instant from, boolean businessHoursOnly,
                                Instant firstResponseDueAt, Instant resolutionDueAt) {
    }
}
