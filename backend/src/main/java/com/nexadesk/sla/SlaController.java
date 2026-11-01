package com.nexadesk.sla;

import com.nexadesk.category.PriorityCode;
import com.nexadesk.sla.dto.SlaDtos.CalendarDto;
import com.nexadesk.sla.dto.SlaDtos.CalendarRequest;
import com.nexadesk.sla.dto.SlaDtos.HolidayDto;
import com.nexadesk.sla.dto.SlaDtos.HolidayRequest;
import com.nexadesk.sla.dto.SlaDtos.SlaPreviewDto;
import com.nexadesk.sla.dto.SlaDtos.SlaRuleDto;
import com.nexadesk.sla.dto.SlaDtos.SlaRuleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sla")
@RequiredArgsConstructor
@Tag(name = "SLA", description = "Règles de délai, horaires ouvrés et jours fériés")
public class SlaController {

    private final SlaAdminService service;

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Règles SLA par priorité")
    public List<SlaRuleDto> rules() {
        return service.rules();
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier une règle SLA", description = "S'applique aux tickets créés ensuite.")
    public SlaRuleDto updateRule(@PathVariable Long id, @Valid @RequestBody SlaRuleRequest request) {
        return service.updateRule(id, request);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Horaires ouvrés")
    public CalendarDto calendar() {
        return service.calendar();
    }

    @PutMapping("/calendar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier les horaires ouvrés")
    public CalendarDto updateCalendar(@Valid @RequestBody CalendarRequest request) {
        return service.updateCalendar(request);
    }

    @GetMapping("/holidays")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Jours fériés")
    public List<HolidayDto> holidays() {
        return service.holidays();
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Déclarer un jour férié")
    public HolidayDto addHoliday(@Valid @RequestBody HolidayRequest request) {
        return service.addHoliday(request);
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un jour férié")
    public void deleteHoliday(@PathVariable Long id) {
        service.deleteHoliday(id);
    }

    @GetMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simuler les échéances d'un ticket",
            description = "Pour vérifier la configuration : échéances d'un ticket de cette priorité créé à « from ».")
    public SlaPreviewDto preview(@RequestParam PriorityCode priority, @RequestParam(required = false) Instant from) {
        return service.preview(priority, from);
    }
}
