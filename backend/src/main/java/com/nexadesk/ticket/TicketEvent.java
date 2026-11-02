package com.nexadesk.ticket;

/**
 * Domain event published by the ticket and SLA services when something worth telling people happens.
 * Listeners (notifications) run after the transaction commits, so they never react to a rolled-back change.
 *
 * @param actorId null when the system acted (SLA monitor, auto-close, automatic resume)
 */
public record TicketEvent(Kind kind, Long ticketId, Long actorId, TicketStatus from, TicketStatus to,
                          Long previousAssigneeId) {

    public enum Kind { CREATED, ASSIGNED, STATUS_CHANGED, PUBLIC_COMMENT, SLA_WARNING, SLA_BREACHED }

    public static TicketEvent created(Ticket ticket, Long actorId) {
        return new TicketEvent(Kind.CREATED, ticket.getId(), actorId, null, null, null);
    }

    public static TicketEvent assigned(Ticket ticket, Long actorId, Long previousAssigneeId) {
        return new TicketEvent(Kind.ASSIGNED, ticket.getId(), actorId, null, null, previousAssigneeId);
    }

    public static TicketEvent statusChanged(Ticket ticket, Long actorId, TicketStatus from, TicketStatus to) {
        return new TicketEvent(Kind.STATUS_CHANGED, ticket.getId(), actorId, from, to, null);
    }

    public static TicketEvent publicComment(Ticket ticket, Long authorId) {
        return new TicketEvent(Kind.PUBLIC_COMMENT, ticket.getId(), authorId, null, null, null);
    }

    public static TicketEvent sla(Ticket ticket, boolean breached) {
        return new TicketEvent(breached ? Kind.SLA_BREACHED : Kind.SLA_WARNING, ticket.getId(), null, null, null, null);
    }
}
