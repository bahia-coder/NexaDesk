package com.nexadesk.ticket;

import java.time.Instant;

/**
 * SLA tracking hooks called by the ticket module, implemented by the SLA module (dependency inversion: the
 * ticket module does not depend on the SLA module). All calls happen inside the ticket's transaction.
 */
public interface TicketSla {

    /** Computes the deadlines of a ticket about to be inserted (single INSERT, no follow-up UPDATE). */
    void onCreated(Ticket ticket, Instant createdAt);

    /** Pauses / resumes the resolution clock and records the first response. */
    void onStatusChanged(Ticket ticket, TicketStatus from, TicketStatus to);

    /** Recomputes the deadlines with the new priority's rule, keeping the time already paused. */
    void onPriorityChanged(Ticket ticket);

    /** A public answer from the IT team counts as the first response. */
    void onStaffReply(Ticket ticket);

    View view(Ticket ticket);

    /**
     * @param consumedPercent share of the resolution time used so far (may exceed 100), null without SLA
     */
    record View(SlaState state, Instant firstResponseDueAt, Instant firstRespondedAt, boolean firstResponseBreached,
                Instant resolutionDueAt, Integer consumedPercent, boolean businessHoursOnly) {

        public static final View NONE = new View(SlaState.NONE, null, null, false, null, null, false);
    }
}
