package com.nexadesk.ticket.dto;

import com.nexadesk.category.Level;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.ticket.PendingReason;
import com.nexadesk.ticket.TicketAction;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.TicketType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;

/** Request and response records of the ticket API. Entities never leave the service layer. */
public final class TicketDtos {

    private TicketDtos() {
    }

    // ---- Shared references -------------------------------------------------------------------------------

    public record UserRefDto(Long id, String fullName, String avatarColor) {
    }

    public record PriorityRefDto(PriorityCode code, String label, String color, int rank) {
    }

    public record SubCategoryRefDto(Long id, String name) {
    }

    // ---- Responses ---------------------------------------------------------------------------------------

    public record TicketSummaryDto(Long id, String reference, TicketType type, String title, TicketStatus status,
                                   PriorityRefDto priority, CategoryRefDto category, UserRefDto requester,
                                   UserRefDto assignee, Instant createdAt, Instant updatedAt) {
    }

    public record FeedbackDto(int rating, String comment, Instant createdAt) {
    }

    public record TicketDetailDto(Long id, String reference, TicketType type, String title, String description,
                                  TicketStatus status, Level impact, Level urgency, PriorityRefDto priority,
                                  CategoryRefDto category, SubCategoryRefDto subcategory, UserRefDto requester,
                                  UserRefDto assignee, PendingReason pendingReason, String rejectionReason,
                                  String resolution, Instant resolvedAt, Instant closedAt, int reopenCount,
                                  int version, Instant createdAt, Instant updatedAt, FeedbackDto feedback,
                                  Set<TicketAction> availableActions) {
    }

    public record CommentDto(Long id, UserRefDto author, String body, boolean internal, Instant createdAt) {
    }

    public record AttachmentDto(Long id, String originalName, String contentType, long sizeBytes,
                                UserRefDto uploadedBy, Instant createdAt) {
    }

    public record HistoryDto(Long id, UserRefDto actor, String action, String field, String oldValue,
                             String newValue, String comment, Instant createdAt) {
    }

    // ---- Requests ----------------------------------------------------------------------------------------

    public enum TicketScope { ALL, MINE, ASSIGNED_TO_ME, UNASSIGNED }

    /** List filters, all optional and combinable. Multi-valued: {@code status=NEW&status=PENDING}. */
    public record TicketFilter(
            String search,
            List<TicketStatus> status,
            List<PriorityCode> priority,
            TicketType type,
            Long categoryId,
            Long assigneeId,
            Long requesterId,
            TicketScope scope,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    }

    public record CreateTicketRequest(
            @NotNull(message = "Le type est obligatoire.") TicketType type,
            @NotBlank(message = "Le titre est obligatoire.")
            @Size(min = 5, max = 150, message = "Le titre doit contenir entre 5 et 150 caractères.") String title,
            @NotBlank(message = "La description est obligatoire.")
            @Size(min = 10, max = 5000, message = "La description doit contenir entre 10 et 5000 caractères.")
            String description,
            @NotNull(message = "La catégorie est obligatoire.") Long categoryId,
            Long subcategoryId,
            @NotNull(message = "L'impact est obligatoire.") Level impact,
            @NotNull(message = "L'urgence est obligatoire.") Level urgency,
            /* Technicians and admins may open a ticket on behalf of an employee. */
            Long requesterId) {
    }

    /** {@code version} enables optimistic locking: an edit based on a stale copy is rejected (409). */
    public record UpdateTicketRequest(
            @NotBlank @Size(min = 5, max = 150, message = "Le titre doit contenir entre 5 et 150 caractères.")
            String title,
            @NotBlank @Size(min = 10, max = 5000, message = "La description doit contenir entre 10 et 5000 caractères.")
            String description,
            @NotNull Integer version) {
    }

    public record ReclassifyRequest(
            @NotNull(message = "La catégorie est obligatoire.") Long categoryId,
            Long subcategoryId,
            @NotNull(message = "La priorité est obligatoire.") PriorityCode priority,
            @NotBlank(message = "Le motif est obligatoire.") @Size(max = 1000) String reason) {
    }

    public record AssignRequest(@NotNull(message = "Le technicien est obligatoire.") Long technicianId,
                                @Size(max = 1000) String comment) {
    }

    /**
     * Workflow transition. Depending on the target: {@code pendingReason} (PENDING), {@code resolution}
     * (RESOLVED) or {@code reason} (REJECTED) is required.
     */
    public record StatusChangeRequest(
            @NotNull(message = "Le statut cible est obligatoire.") TicketStatus targetStatus,
            @Size(max = 1000) String reason,
            PendingReason pendingReason,
            @Size(max = 5000) String resolution) {
    }

    public record ReasonRequest(@NotBlank(message = "Le motif est obligatoire.") @Size(max = 1000) String reason) {
    }

    public record CommentRequest(
            @NotBlank(message = "Le message est vide.") @Size(max = 5000) String body,
            boolean internal) {
    }

    public record FeedbackRequest(
            @Min(value = 1, message = "La note va de 1 à 5.") @Max(value = 5, message = "La note va de 1 à 5.")
            int rating,
            @Size(max = 1000) String comment) {
    }
}
