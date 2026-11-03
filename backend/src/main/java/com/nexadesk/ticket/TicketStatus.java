package com.nexadesk.ticket;

import java.util.EnumSet;
import java.util.Set;

/** NEW → ASSIGNED → IN_PROGRESS ⇄ PENDING → RESOLVED → CLOSED, plus REJECTED. */
public enum TicketStatus {
    NEW("Nouveau"),
    ASSIGNED("Assigné"),
    IN_PROGRESS("En cours"),
    PENDING("En attente"),
    RESOLVED("Résolu"),
    CLOSED("Fermé"),
    REJECTED("Rejeté");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    /** French label, as the interface shows it (audit trail summaries). */
    public String label() {
        return label;
    }

    /** Statuses in which the IT team still has work to do. */
    public static final Set<TicketStatus> OPEN = EnumSet.of(NEW, ASSIGNED, IN_PROGRESS, PENDING);

    /** Columns of the Kanban board. */
    public static final Set<TicketStatus> BOARD = EnumSet.of(NEW, ASSIGNED, IN_PROGRESS, PENDING, RESOLVED);

    /** Read-only: only the reopen procedure can bring a CLOSED ticket back. */
    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED;
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
