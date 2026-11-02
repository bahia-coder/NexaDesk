package com.nexadesk.ai;

import com.nexadesk.intervention.dto.InterventionDtos.InterventionDto;
import com.nexadesk.ticket.dto.TicketDtos.CommentDto;
import com.nexadesk.ticket.dto.TicketDtos.HistoryDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Everything known about a ticket, as seen by a technician or an admin (internal notes included), loaded through
 * the ticket services so their access rules apply.
 */
record TicketContext(TicketDetailDto ticket, List<CommentDto> comments, List<HistoryDto> history,
                     List<InterventionDto> interventions, long totalMinutes) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);
    private static final int MAX_COMMENTS = 20;

    /**
     * Text sent to the language model. Data minimisation: people appear by role (requester, technician),
     * never by name or e-mail.
     */
    String forModel(ZoneId zone) {
        TicketDetailDto t = ticket;
        StringBuilder text = new StringBuilder()
                .append("Ticket ").append(t.reference()).append(" (").append(AiLabels.type(t.type())).append(")\n")
                .append("Titre : ").append(t.title()).append('\n')
                .append("Description : ").append(t.description()).append('\n')
                .append("Statut : ").append(AiLabels.status(t.status()))
                .append(t.pendingReason() == null ? "" : " – " + AiLabels.pending(t.pendingReason())).append('\n')
                .append("Priorité : ").append(t.priority().label()).append(" ; catégorie : ")
                .append(t.category().name()).append('\n')
                .append("Ouvert le ").append(DATE_TIME.format(t.createdAt().atZone(zone))).append('\n');
        if (t.resolution() != null) {
            text.append("Solution enregistrée : ").append(t.resolution()).append('\n');
        }
        List<CommentDto> recent = comments.subList(Math.max(0, comments.size() - MAX_COMMENTS), comments.size());
        if (!recent.isEmpty()) {
            text.append("\nÉchanges (du plus ancien au plus récent) :\n");
            recent.forEach(c -> text.append("- ").append(DATE_TIME.format(c.createdAt().atZone(zone))).append(" [")
                    .append(role(c.author())).append(c.internal() ? ", note interne" : "").append("] ")
                    .append(c.body().replace('\n', ' ')).append('\n'));
        }
        if (!interventions.isEmpty()) {
            text.append("\nInterventions techniques :\n");
            interventions.forEach(i -> text.append("- ").append(DATE_TIME.format(i.createdAt().atZone(zone)))
                    .append(" (").append(i.timeSpentMinutes()).append(" min) diagnostic : ").append(i.diagnosis())
                    .append(i.solution() == null ? "" : " ; solution : " + i.solution()).append('\n'));
        }
        if (!history.isEmpty()) {
            text.append("\nHistorique : ").append(history.stream()
                    .map(h -> AiLabels.action(h.action()) + (h.newValue() == null ? "" : " (" + h.newValue() + ")"))
                    .collect(Collectors.joining(", "))).append('\n');
        }
        return text.toString();
    }

    String role(UserRefDto author) {
        if (author == null) {
            return "Système";
        }
        if (ticket.requester() != null && author.id().equals(ticket.requester().id())) {
            return "Demandeur";
        }
        if (ticket.assignee() != null && author.id().equals(ticket.assignee().id())) {
            return "Technicien assigné";
        }
        return "Support";
    }
}
