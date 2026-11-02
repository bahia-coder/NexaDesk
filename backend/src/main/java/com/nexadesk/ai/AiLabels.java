package com.nexadesk.ai;

import com.nexadesk.ticket.PendingReason;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.TicketType;

/** French wording used in summaries and prompts (the UI has its own labels). */
final class AiLabels {

    private AiLabels() {
    }

    static String type(TicketType type) {
        return switch (type) {
            case INCIDENT -> "incident";
            case SERVICE_REQUEST -> "demande de service";
        };
    }

    static String status(TicketStatus status) {
        return switch (status) {
            case NEW -> "nouveau";
            case ASSIGNED -> "assigné";
            case IN_PROGRESS -> "en cours";
            case PENDING -> "en attente";
            case RESOLVED -> "résolu";
            case CLOSED -> "fermé";
            case REJECTED -> "rejeté";
        };
    }

    static String pending(PendingReason reason) {
        return switch (reason) {
            case WAITING_USER -> "attente d'une réponse du demandeur";
            case WAITING_VENDOR -> "attente d'un fournisseur";
            case WAITING_PART -> "attente d'une pièce";
            case OTHER -> "autre motif";
        };
    }

    /** History actions are stored as enum names ({@code HistoryAction}). */
    static String action(String action) {
        return switch (action) {
            case "CREATED" -> "création";
            case "UPDATED" -> "modification";
            case "CATEGORY_CHANGED" -> "changement de catégorie";
            case "PRIORITY_CHANGED" -> "changement de priorité";
            case "ASSIGNED" -> "affectation";
            case "REASSIGNED" -> "réaffectation";
            case "STATUS_CHANGED" -> "changement de statut";
            case "REOPENED" -> "réouverture";
            case "COMMENT_ADDED" -> "commentaire";
            case "ATTACHMENT_ADDED" -> "pièce jointe ajoutée";
            case "ATTACHMENT_REMOVED" -> "pièce jointe retirée";
            case "INTERVENTION_ADDED" -> "intervention";
            case "INTERVENTION_UPDATED" -> "intervention modifiée";
            case "FEEDBACK_ADDED" -> "évaluation";
            case "SLA_WARNING" -> "alerte SLA";
            case "SLA_BREACHED" -> "SLA dépassé";
            default -> action.toLowerCase();
        };
    }
}
