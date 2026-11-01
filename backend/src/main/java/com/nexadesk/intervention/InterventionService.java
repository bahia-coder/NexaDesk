package com.nexadesk.intervention;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.intervention.dto.InterventionDtos.InterventionDto;
import com.nexadesk.intervention.dto.InterventionDtos.InterventionRequest;
import com.nexadesk.intervention.dto.InterventionDtos.MyInterventionsDto;
import com.nexadesk.intervention.dto.InterventionDtos.TicketInterventionsDto;
import com.nexadesk.ticket.SolutionSource;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketAccess;
import com.nexadesk.ticket.TicketAction;
import com.nexadesk.ticket.TicketMapper;
import com.nexadesk.ticket.TicketPolicy;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Technicians' work log. Interventions contain technical diagnoses, so they are staff-only: the requester
 * sees the outcome through the ticket's resolution.
 */
@Service
public class InterventionService implements SolutionSource {

    /** Clock skew tolerated between the browser and the server when validating "not in the future". */
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);
    private static final int DEFAULT_PERIOD_DAYS = 30;

    private final InterventionRepository repository;
    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketHistoryRecorder history;
    private final TicketMapper ticketMapper;
    private final Clock clock;
    private final ZoneId zone;

    public InterventionService(InterventionRepository repository, TicketAccess access, TicketPolicy policy,
                               TicketHistoryRecorder history, TicketMapper ticketMapper, Clock clock,
                               @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.repository = repository;
        this.access = access;
        this.policy = policy;
        this.history = history;
        this.ticketMapper = ticketMapper;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public TicketInterventionsDto list(Long ticketId) {
        User actor = staff();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        List<InterventionDto> items = repository.findByTicket_IdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .map(intervention -> toDto(intervention, actor, ticket))
                .toList();
        long total = items.stream().mapToLong(InterventionDto::timeSpentMinutes).sum();
        return new TicketInterventionsDto(items, total);
    }

    @Transactional
    public InterventionDto add(Long ticketId, InterventionRequest request) {
        User actor = staff();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        Instant now = clock.instant();
        policy.require(TicketAction.LOG_INTERVENTION, ticket, actor, now);
        validatePeriod(request, now);

        Intervention intervention = new Intervention(ticket, actor);
        apply(intervention, request);
        repository.save(intervention);
        history.recordInternal(ticket, actor, HistoryAction.INTERVENTION_ADDED,
                "Intervention : " + request.timeSpentMinutes() + " min");
        return toDto(intervention, actor, ticket);
    }

    @Transactional
    public InterventionDto update(Long ticketId, Long interventionId, InterventionRequest request) {
        User actor = staff();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        Intervention intervention = repository.findByIdAndTicket_Id(interventionId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention", interventionId));
        if (!isEditable(intervention, actor, ticket)) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Seul l'auteur ou un administrateur peut modifier cette intervention, tant que le ticket "
                            + "n'est pas fermé.");
        }
        validatePeriod(request, clock.instant());
        apply(intervention, request);
        repository.flush();
        history.recordInternal(ticket, actor, HistoryAction.INTERVENTION_UPDATED,
                "Intervention modifiée : " + request.timeSpentMinutes() + " min");
        return toDto(intervention, actor, ticket);
    }

    /** The current user's own interventions over a period (default: the last 30 days). */
    @Transactional(readOnly = true)
    public MyInterventionsDto mine(LocalDate from, LocalDate to, Pageable pageable) {
        User actor = staff();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La date de début doit précéder la date de fin.");
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();

        PageResponse<InterventionDto> page = PageResponse.from(
                repository.findByTechnicianBetween(actor.getId(), fromInstant, toInstant, pageable)
                        .map(intervention -> toDto(intervention, actor, intervention.getTicket())));
        long total = repository.sumMinutesByTechnicianBetween(actor.getId(), fromInstant, toInstant);
        return new MyInterventionsDto(page, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> latestSolution(Long ticketId) {
        return repository.findFirstByTicket_IdAndSolutionIsNotNullOrderByCreatedAtDescIdDesc(ticketId)
                .map(Intervention::getSolution);
    }

    private User staff() {
        User actor = access.actor();
        if (!TicketAccess.isStaff(actor)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    private static boolean isEditable(Intervention intervention, User actor, Ticket ticket) {
        boolean authorOrAdmin = intervention.getTechnician().getId().equals(actor.getId())
                || actor.getRoleCode() == RoleCode.ADMIN;
        return authorOrAdmin && !ticket.getStatus().isTerminal();
    }

    private void validatePeriod(InterventionRequest request, Instant now) {
        Instant latestAllowed = now.plus(CLOCK_TOLERANCE);
        if ((request.startedAt() != null && request.startedAt().isAfter(latestAllowed))
                || (request.endedAt() != null && request.endedAt().isAfter(latestAllowed))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Une intervention ne peut pas être dans le futur.");
        }
        if (request.startedAt() != null && request.endedAt() != null
                && !request.endedAt().isAfter(request.startedAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La fin doit être postérieure au début.");
        }
    }

    private static void apply(Intervention intervention, InterventionRequest request) {
        intervention.setDiagnosis(request.diagnosis().trim());
        intervention.setSolution(request.solution() == null || request.solution().isBlank()
                ? null : request.solution().trim());
        intervention.setTimeSpentMinutes(request.timeSpentMinutes());
        intervention.setStartedAt(request.startedAt());
        intervention.setEndedAt(request.endedAt());
    }

    private InterventionDto toDto(Intervention i, User actor, Ticket ticket) {
        return new InterventionDto(i.getId(), ticket.getId(), ticket.getReference(), ticket.getTitle(),
                ticketMapper.user(i.getTechnician()), i.getDiagnosis(), i.getSolution(), i.getTimeSpentMinutes(),
                i.getStartedAt(), i.getEndedAt(), i.getCreatedAt(), i.getUpdatedAt(), isEditable(i, actor, ticket));
    }
}
