package com.nexadesk.ai;

import com.nexadesk.intervention.dto.InterventionDtos.InterventionDto;
import com.nexadesk.ticket.dto.TicketDtos.HistoryDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extractive summary: facts picked from the ticket (status, exchanges, last diagnosis, solution), no generated
 * text. Pure logic, unit-tested; labelled "résumé automatique (non IA)" in the UI.
 */
final class HeuristicSummary {

    record Summary(String text, List<String> keyPoints) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRANCE);
    private static final int KEY_POINTS = 4;
    private static final int EXCERPT = 220;

    private HeuristicSummary() {
    }

    static Summary summarize(TicketContext context, ZoneId zone) {
        TicketDetailDto t = context.ticket();
        List<String> sentences = new ArrayList<>();
        sentences.add(capitalize(AiLabels.type(t.type())) + " « " + t.title() + " » ouvert le "
                + DATE.format(t.createdAt().atZone(zone)) + " : statut " + AiLabels.status(t.status())
                + ", priorité " + t.priority().label().toLowerCase(Locale.ROOT) + ", catégorie "
                + t.category().name() + ".");
        sentences.add(t.assignee() == null ? "Pas encore affecté à un technicien."
                : "Pris en charge par " + t.assignee().fullName() + ".");

        long publicMessages = context.comments().stream().filter(c -> !c.internal()).count();
        long notes = context.comments().size() - publicMessages;
        sentences.add(plural(publicMessages, "message public", "messages publics") + ", "
                + plural(notes, "note interne", "notes internes") + ", "
                + plural(context.interventions().size(), "intervention", "interventions")
                + (context.totalMinutes() > 0 ? " (" + context.totalMinutes() + " min au total)" : "") + ".");
        if (t.pendingReason() != null) {
            sentences.add("En attente : " + AiLabels.pending(t.pendingReason()) + ".");
        }
        if (!context.interventions().isEmpty()) {
            InterventionDto last = context.interventions().getLast();
            sentences.add(sentence("Dernier diagnostic : " + excerpt(last.diagnosis())));
        }
        context.comments().stream().filter(c -> !c.internal()).reduce((first, second) -> second)
                .filter(c -> t.resolution() == null)
                .ifPresent(c -> sentences.add("Dernier message (" + context.role(c.author()).toLowerCase(Locale.ROOT)
                        + ") : « " + excerpt(c.body()) + " »."));
        if (t.resolution() != null) {
            sentences.add(sentence("Solution : " + excerpt(t.resolution())));
        }

        List<HistoryDto> history = context.history();
        List<String> keyPoints = history.subList(Math.max(0, history.size() - KEY_POINTS), history.size()).stream()
                .map(h -> DATE_TIME.format(h.createdAt().atZone(zone)) + " – " + AiLabels.action(h.action())
                        + (h.oldValue() != null && h.newValue() != null ? " : " + h.oldValue() + " → " + h.newValue()
                        : h.newValue() != null ? " : " + h.newValue() : ""))
                .toList();
        return new Summary(String.join(" ", sentences), keyPoints);
    }

    private static String excerpt(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        String cut = flat.length() <= EXCERPT ? flat : flat.substring(0, EXCERPT).stripTrailing() + "…";
        return cut.endsWith(".") ? cut.substring(0, cut.length() - 1) : cut;
    }

    private static String sentence(String text) {
        return text.endsWith("…") ? text : text + ".";
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count > 1 ? many : one);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
