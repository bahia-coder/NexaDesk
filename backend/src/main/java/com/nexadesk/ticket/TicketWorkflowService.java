package com.nexadesk.ticket;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.ticket.dto.TicketDtos.StatusChangeRequest;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status transitions and assignment. Every method: load a visible ticket → {@link TicketPolicy#require} →
 * apply the change → record history. Permission rules are never duplicated here.
 */
@Service
@RequiredArgsConstructor
public class TicketWorkflowService {

    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketHistoryRecorder history;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final SolutionSource solutionSource;
    private final Clock clock;

    /** A technician takes a new, unassigned ticket of one of their specialties. */
    @Transactional
    public TicketDetailDto take(Long id) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.TAKE, ticket, actor, clock.instant());
        ticket.setAssignee(actor);
        moveTo(ticket, actor, TicketStatus.ASSIGNED, "Prise en charge");
        history.change(ticket, actor, HistoryAction.ASSIGNED, "assignee", null, actor.getFullName(), null);
        return ticketService.detailAfterChange(ticket, actor);
    }

    /** Admin assignment or reassignment of any open ticket. A NEW ticket becomes ASSIGNED. */
    @Transactional
    public TicketDetailDto assign(Long id, Long technicianId, String comment) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.ASSIGN, ticket, actor, clock.instant());
        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", technicianId));
        if (technician.getRoleCode() != RoleCode.TECHNICIAN || !technician.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_ASSIGNEE);
        }
        if (ticket.isAssignedTo(technicianId)) {
            return ticketService.detail(ticket, actor);
        }

        User previous = ticket.getAssignee();
        ticket.setAssignee(technician);
        history.change(ticket, actor, previous == null ? HistoryAction.ASSIGNED : HistoryAction.REASSIGNED,
                "assignee", previous == null ? null : previous.getFullName(), technician.getFullName(),
                blankToNull(comment));
        if (ticket.getStatus() == TicketStatus.NEW) {
            moveTo(ticket, actor, TicketStatus.ASSIGNED, null);
        }
        return ticketService.detailAfterChange(ticket, actor);
    }

    /** Handler transitions: start, put on hold, resume, resolve, reject, close. */
    @Transactional
    public TicketDetailDto changeStatus(Long id, StatusChangeRequest request) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        Instant now = clock.instant();
        TicketStatus target = request.targetStatus();
        TicketAction action = actionFor(ticket.getStatus(), target);
        policy.require(action, ticket, actor, now);

        switch (action) {
            case START -> moveTo(ticket, actor, TicketStatus.IN_PROGRESS, blankToNull(request.reason()));
            case PUT_ON_HOLD -> {
                if (request.pendingReason() == null) {
                    throw required("Le motif de mise en attente (pendingReason) est obligatoire.");
                }
                ticket.setPendingReason(request.pendingReason());
                moveTo(ticket, actor, TicketStatus.PENDING, describe(request.pendingReason(), request.reason()));
            }
            case RESUME -> {
                ticket.setPendingReason(null);
                moveTo(ticket, actor, TicketStatus.IN_PROGRESS, blankToNull(request.reason()));
            }
            case RESOLVE -> {
                String resolution = Optional.ofNullable(blankToNull(request.resolution()))
                        .or(() -> solutionSource.latestSolution(ticket.getId()))
                        .orElse(null);
                if (resolution == null || resolution.length() < 10) {
                    throw required("Décrivez la solution appliquée (10 caractères minimum), "
                            + "ou renseignez-la dans une intervention.");
                }
                ticket.setResolution(resolution);
                ticket.setResolvedAt(now);
                moveTo(ticket, actor, TicketStatus.RESOLVED, null);
            }
            case REJECT -> {
                String reason = blankToNull(request.reason());
                if (reason == null) {
                    throw required("Le motif du rejet est obligatoire.");
                }
                ticket.setRejectionReason(reason);
                moveTo(ticket, actor, TicketStatus.REJECTED, reason);
            }
            case CLOSE -> close(ticket, actor, now, blankToNull(request.reason()));
            default -> throw new IllegalStateException("Unexpected action " + action);
        }
        return ticketService.detailAfterChange(ticket, actor);
    }

    @Transactional
    public TicketDetailDto confirmResolution(Long id) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        Instant now = clock.instant();
        policy.require(TicketAction.CONFIRM_RESOLUTION, ticket, actor, now);
        close(ticket, actor, now, "Résolution confirmée par le demandeur");
        return ticketService.detailAfterChange(ticket, actor);
    }

    /** The requester says the problem is not solved: work resumes with the same technician. */
    @Transactional
    public TicketDetailDto rejectResolution(Long id, String reason) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.REJECT_RESOLUTION, ticket, actor, clock.instant());
        ticket.setResolution(null);
        ticket.setResolvedAt(null);
        moveTo(ticket, actor, TicketStatus.IN_PROGRESS, "Résolution refusée : " + reason.trim());
        return ticketService.detailAfterChange(ticket, actor);
    }

    /**
     * Brings a CLOSED ticket back. It returns to its last technician if still active, otherwise to the
     * unassigned queue.
     */
    @Transactional
    public TicketDetailDto reopen(Long id, String reason) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.REOPEN, ticket, actor, clock.instant());

        TicketStatus previous = ticket.getStatus();
        boolean assigneeAvailable = ticket.getAssignee() != null && ticket.getAssignee().isActive();
        if (!assigneeAvailable) {
            ticket.setAssignee(null);
        }
        ticket.setStatus(assigneeAvailable ? TicketStatus.IN_PROGRESS : TicketStatus.NEW);
        ticket.setResolution(null);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        ticket.setReopenCount(ticket.getReopenCount() + 1);
        history.change(ticket, actor, HistoryAction.REOPENED, "status", previous, ticket.getStatus(), reason.trim());
        return ticketService.detailAfterChange(ticket, actor);
    }

    private void close(Ticket ticket, User actor, Instant now, String comment) {
        ticket.setClosedAt(now);
        moveTo(ticket, actor, TicketStatus.CLOSED, comment);
    }

    private void moveTo(Ticket ticket, User actor, TicketStatus target, String comment) {
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(target);
        history.change(ticket, actor, HistoryAction.STATUS_CHANGED, "status", previous, target, comment);
    }

    /** Maps "go to status X" onto the workflow action it represents from the current status. */
    static TicketAction actionFor(TicketStatus current, TicketStatus target) {
        return switch (target) {
            case IN_PROGRESS -> current == TicketStatus.PENDING ? TicketAction.RESUME : TicketAction.START;
            case PENDING -> TicketAction.PUT_ON_HOLD;
            case RESOLVED -> TicketAction.RESOLVE;
            case REJECTED -> TicketAction.REJECT;
            case CLOSED -> TicketAction.CLOSE;
            case NEW, ASSIGNED -> throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Utilisez la prise en charge ou l'affectation pour passer un ticket en " + target + ".");
        };
    }

    private static String describe(Enum<?> pendingReason, String reason) {
        String detail = blankToNull(reason);
        return detail == null ? pendingReason.name() : pendingReason.name() + " — " + detail;
    }

    private static BusinessException required(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
