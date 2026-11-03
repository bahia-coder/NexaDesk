package com.nexadesk.audit;

import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketEvent;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Copies the important ticket actions into the audit trail. A plain (synchronous) {@code @EventListener}: it runs
 * inside the ticket's transaction, so the audit entry commits — or rolls back — with the change itself.
 * SLA alerts are system signals, not actions, and are not audited.
 */
@Component
@RequiredArgsConstructor
public class TicketAuditListener {

    private static final String ENTITY = "TICKET";

    private final AuditService auditService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @EventListener
    public void on(TicketEvent event) {
        AuditAction action = switch (event.kind()) {
            case CREATED -> AuditAction.TICKET_CREATED;
            case ASSIGNED -> AuditAction.TICKET_ASSIGNED;
            case STATUS_CHANGED -> AuditAction.TICKET_STATUS_CHANGED;
            case PUBLIC_COMMENT -> AuditAction.TICKET_COMMENTED;
            case SLA_WARNING, SLA_BREACHED -> null;
        };
        if (action == null) {
            return;
        }
        Ticket ticket = ticketRepository.getReferenceById(event.ticketId());
        User actor = event.actorId() == null ? null : userRepository.getReferenceById(event.actorId());
        String email = actor == null ? null : actor.getEmail();
        auditService.recordFor(actor, email, action, ENTITY, ticket.getReference(), summary(event, ticket));
    }

    private static String summary(TicketEvent event, Ticket ticket) {
        String ref = ticket.getReference();
        return switch (event.kind()) {
            case CREATED -> "Création du ticket " + ref + " : " + ticket.getTitle();
            case ASSIGNED -> "Ticket " + ref + " affecté à "
                    + (ticket.getAssignee() == null ? "personne" : ticket.getAssignee().getFullName());
            case STATUS_CHANGED -> "Ticket " + ref + " : " + label(event.from()) + " → " + label(event.to())
                    + (event.actorId() == null ? " (automatique)" : "");
            case PUBLIC_COMMENT -> "Commentaire public sur " + ref;
            case SLA_WARNING, SLA_BREACHED -> "";
        };
    }

    private static String label(TicketStatus status) {
        return status == null ? "—" : status.label();
    }
}
