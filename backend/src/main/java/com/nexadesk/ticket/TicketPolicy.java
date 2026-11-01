package com.nexadesk.ticket;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "who may do what on a ticket, in which state". Pure logic (no database),
 * exhaustively unit-tested. Services call {@link #require}; the API exposes {@link #availableActions}.
 *
 * <p>Visibility: an admin sees everything; an employee sees the tickets they requested; a technician sees the
 * tickets they requested, those assigned to them and those in their specialties.
 */
@Component
public class TicketPolicy {

    private final TicketProperties properties;

    public TicketPolicy(TicketProperties properties) {
        this.properties = properties;
    }

    public boolean canView(Ticket ticket, User user) {
        if (isAdmin(user) || ticket.isRequestedBy(user.getId())) {
            return true;
        }
        return isTechnician(user)
                && (ticket.isAssignedTo(user.getId()) || user.hasSpecialty(ticket.getCategory().getId()));
    }

    public Set<TicketAction> availableActions(Ticket ticket, User user, Instant now) {
        Set<TicketAction> actions = EnumSet.noneOf(TicketAction.class);
        if (!canView(ticket, user)) {
            return actions;
        }
        for (TicketAction action : TicketAction.values()) {
            if (isAllowed(action, ticket, user, now)) {
                actions.add(action);
            }
        }
        return actions;
    }

    public void require(TicketAction action, Ticket ticket, User user, Instant now) {
        if (!canView(ticket, user) || !isAllowed(action, ticket, user, now)) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED, denialMessage(action, ticket));
        }
    }

    boolean isAllowed(TicketAction action, Ticket ticket, User user, Instant now) {
        TicketStatus status = ticket.getStatus();
        boolean admin = isAdmin(user);
        boolean requester = ticket.isRequestedBy(user.getId());
        boolean assignee = ticket.isAssignedTo(user.getId());
        boolean handler = admin || assignee;
        boolean staff = admin || isTechnician(user);

        return switch (action) {
            case EDIT -> (requester && status == TicketStatus.NEW) || (handler && !status.isTerminal());
            case RECLASSIFY -> handler && !status.isTerminal();
            case TAKE -> isTechnician(user) && status == TicketStatus.NEW && ticket.getAssignee() == null
                    && user.hasSpecialty(ticket.getCategory().getId());
            case ASSIGN -> admin && status.isOpen();
            case START -> handler && status == TicketStatus.ASSIGNED;
            case PUT_ON_HOLD, RESOLVE -> handler && status == TicketStatus.IN_PROGRESS;
            case RESUME -> handler && status == TicketStatus.PENDING;
            case REJECT -> (admin && (status == TicketStatus.NEW || status == TicketStatus.ASSIGNED))
                    || (assignee && status == TicketStatus.ASSIGNED);
            case CLOSE -> admin && status == TicketStatus.RESOLVED;
            case CONFIRM_RESOLUTION, REJECT_RESOLUTION -> requester && status == TicketStatus.RESOLVED;
            case REOPEN -> status == TicketStatus.CLOSED && (admin || (requester && withinReopenWindow(ticket, now)));
            case COMMENT -> !status.isTerminal();
            case COMMENT_INTERNAL -> staff && !status.isTerminal();
            case ADD_ATTACHMENT -> (requester || handler) && !status.isTerminal();
            case GIVE_FEEDBACK -> requester && (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED);
        };
    }

    private boolean withinReopenWindow(Ticket ticket, Instant now) {
        return ticket.getClosedAt() != null && ticket.getClosedAt().plus(properties.reopenWindow()).isAfter(now);
    }

    private static boolean isAdmin(User user) {
        return user.getRoleCode() == RoleCode.ADMIN;
    }

    private static boolean isTechnician(User user) {
        return user.getRoleCode() == RoleCode.TECHNICIAN;
    }

    private static String denialMessage(TicketAction action, Ticket ticket) {
        if (ticket.getStatus().isTerminal() && action != TicketAction.REOPEN) {
            return "Le ticket " + ticket.getReference() + " est fermé : il n'est plus modifiable.";
        }
        return switch (action) {
            case EDIT -> "Ce ticket ne peut plus être modifié par vous dans son état actuel.";
            case TAKE -> "Seul un technicien de cette spécialité peut prendre en charge un ticket nouveau non assigné.";
            case REOPEN -> "Ce ticket ne peut plus être rouvert (délai dépassé ou droits insuffisants).";
            case GIVE_FEEDBACK -> "Seul le demandeur peut évaluer un ticket résolu.";
            default -> "Action impossible sur le ticket " + ticket.getReference() + " (statut "
                    + ticket.getStatus() + ").";
        };
    }
}
