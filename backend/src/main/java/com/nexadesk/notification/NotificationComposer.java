package com.nexadesk.notification;

import com.nexadesk.ticket.TicketEvent;
import com.nexadesk.ticket.TicketStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Who is told what, for each ticket event. Pure logic (no database, no Spring), unit-tested on its own.
 * The person who acted is never notified of their own action.
 */
public final class NotificationComposer {

    /** What the composer needs to know about the ticket and the people around it. */
    public record Context(TicketEvent event, String reference, String title, String priorityLabel,
                          Long requesterId, Long assigneeId, String actorName, String assigneeName,
                          Collection<Long> adminIds, Collection<Long> specialistIds) {
    }

    public record Draft(Long recipientId, NotificationType type, String title, String message) {
    }

    private static final Map<TicketStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            TicketStatus.NEW, "nouveau",
            TicketStatus.ASSIGNED, "assigné",
            TicketStatus.IN_PROGRESS, "en cours de traitement",
            TicketStatus.PENDING, "en attente",
            TicketStatus.RESOLVED, "résolu",
            TicketStatus.CLOSED, "fermé",
            TicketStatus.REJECTED, "rejeté"));

    private NotificationComposer() {
    }

    public static List<Draft> compose(Context c) {
        List<Draft> drafts = new ArrayList<>();
        TicketEvent e = c.event();
        String ref = c.reference();
        switch (e.kind()) {
            case CREATED -> {
                // Opened on behalf of an employee: they must know a ticket exists for them.
                add(drafts, c, c.requesterId(), NotificationType.TICKET_CREATED, "Ticket " + ref + " ouvert pour vous",
                        c.actorName() + " a ouvert « " + c.title() + " » en votre nom.");
                Set<Long> recipients = new LinkedHashSet<>(c.adminIds());
                recipients.addAll(c.specialistIds());
                recipients.forEach(id -> add(drafts, c, id, NotificationType.TICKET_CREATED,
                        "Nouveau ticket " + ref, c.priorityLabel() + " · " + c.title()));
            }
            case ASSIGNED -> {
                add(drafts, c, c.assigneeId(), NotificationType.TICKET_ASSIGNED,
                        "Ticket " + ref + " vous est affecté", c.title());
                if (!Objects.equals(e.previousAssigneeId(), c.assigneeId())) {
                    add(drafts, c, e.previousAssigneeId(), NotificationType.TICKET_ASSIGNED,
                            "Ticket " + ref + " réaffecté", "Désormais suivi par " + c.assigneeName() + ".");
                }
                add(drafts, c, c.requesterId(), NotificationType.TICKET_ASSIGNED,
                        "Votre ticket " + ref + " est pris en charge",
                        c.assigneeName() + " s'occupe de votre demande « " + c.title() + " ».");
            }
            case STATUS_CHANGED -> statusChanged(drafts, c);
            case PUBLIC_COMMENT -> {
                boolean byRequester = Objects.equals(e.actorId(), c.requesterId());
                if (byRequester) {
                    add(drafts, c, c.assigneeId(), NotificationType.NEW_COMMENT,
                            ref + " : nouveau message", c.actorName() + " a répondu sur « " + c.title() + " ».");
                } else {
                    add(drafts, c, c.requesterId(), NotificationType.NEW_COMMENT,
                            "Réponse sur votre ticket " + ref, c.actorName() + " vous a répondu.");
                    add(drafts, c, c.assigneeId(), NotificationType.NEW_COMMENT,
                            ref + " : nouveau message", c.actorName() + " a commenté le ticket.");
                }
            }
            case SLA_WARNING -> {
                Collection<Long> recipients = c.assigneeId() != null ? List.of(c.assigneeId()) : c.adminIds();
                recipients.forEach(id -> add(drafts, c, id, NotificationType.SLA_WARNING,
                        ref + " : échéance proche", "Le délai de résolution de « " + c.title() + " » arrive à son terme."));
            }
            case SLA_BREACHED -> {
                Set<Long> recipients = new LinkedHashSet<>(c.adminIds());
                if (c.assigneeId() != null) {
                    recipients.add(c.assigneeId());
                }
                recipients.forEach(id -> add(drafts, c, id, NotificationType.SLA_BREACHED,
                        ref + " : échéance dépassée", "Le délai de résolution de « " + c.title() + " » est dépassé."));
            }
        }
        return drafts;
    }

    private static void statusChanged(List<Draft> drafts, Context c) {
        TicketEvent e = c.event();
        TicketStatus from = e.from();
        TicketStatus to = e.to();
        String ref = c.reference();
        boolean byRequester = Objects.equals(e.actorId(), c.requesterId());

        if (to == TicketStatus.ASSIGNED) {
            return; // covered by the ASSIGNED event
        }
        if (to == TicketStatus.RESOLVED) {
            add(drafts, c, c.requesterId(), NotificationType.TICKET_RESOLVED, "Votre ticket " + ref + " est résolu",
                    "Confirmez la résolution, ou indiquez si le problème persiste.");
            return;
        }
        if (from == TicketStatus.RESOLVED && to == TicketStatus.CLOSED && byRequester) {
            add(drafts, c, c.assigneeId(), NotificationType.RESOLUTION_CONFIRMED, ref + " : résolution confirmée",
                    c.actorName() + " a confirmé la résolution de « " + c.title() + " ».");
            return;
        }
        if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS && byRequester) {
            add(drafts, c, c.assigneeId(), NotificationType.RESOLUTION_REJECTED, ref + " : résolution refusée",
                    c.actorName() + " indique que le problème persiste.");
            return;
        }
        if (from == TicketStatus.CLOSED) {
            add(drafts, c, c.assigneeId(), NotificationType.STATUS_CHANGED, "Ticket " + ref + " rouvert",
                    "« " + c.title() + " » a été rouvert.");
            if (to == TicketStatus.NEW) {
                c.specialistIds().forEach(id -> add(drafts, c, id, NotificationType.STATUS_CHANGED,
                        "Ticket " + ref + " rouvert, à reprendre", c.title()));
            }
        }
        add(drafts, c, c.requesterId(), NotificationType.STATUS_CHANGED, "Votre ticket " + ref,
                "Statut : " + STATUS_LABELS.get(to) + ".");
        if (e.actorId() != null && !Objects.equals(e.actorId(), c.assigneeId()) && from != TicketStatus.CLOSED) {
            add(drafts, c, c.assigneeId(), NotificationType.STATUS_CHANGED, "Ticket " + ref,
                    "Statut passé à « " + STATUS_LABELS.get(to) + " » par " + c.actorName() + ".");
        }
    }

    /** Adds a draft unless there is no recipient, the recipient is the actor, or they were already told. */
    private static void add(List<Draft> drafts, Context c, Long recipientId, NotificationType type, String title,
                            String message) {
        if (recipientId == null || recipientId.equals(c.event().actorId())) {
            return;
        }
        if (drafts.stream().anyMatch(d -> d.recipientId().equals(recipientId))) {
            return;
        }
        drafts.add(new Draft(recipientId, type, title, message));
    }
}
