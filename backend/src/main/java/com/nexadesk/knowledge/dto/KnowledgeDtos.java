package com.nexadesk.knowledge.dto;

import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.knowledge.ArticleStatus;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    /** What the current user may do on an article (the UI shows only these buttons). */
    public enum ArticleAction { EDIT, PUBLISH, ARCHIVE, MARK_HELPFUL }

    /** RELEVANCE only makes sense with a search text; without one the list falls back to RECENT. */
    public enum ArticleSort { RELEVANCE, RECENT, POPULAR }

    public record ArticleSummaryDto(Long id, String title, String summary, ArticleStatus status,
                                    CategoryRefDto category, UserRefDto author, int viewCount, int helpfulCount,
                                    Instant publishedAt, Instant updatedAt) {
    }

    /** Reference of the ticket an article was drafted from: shown to technicians and admins only. */
    public record SourceTicketDto(Long id, String reference) {
    }

    public record ArticleDto(Long id, String title, String summary, String content, ArticleStatus status,
                             CategoryRefDto category, UserRefDto author, SourceTicketDto sourceTicket,
                             int viewCount, int helpfulCount, long linkedTickets, boolean helpedByMe,
                             Instant publishedAt, Instant createdAt, Instant updatedAt, int version,
                             Set<ArticleAction> availableActions) {
    }

    public record HelpfulDto(int helpfulCount, boolean helpedByMe) {
    }

    /** All filters optional. {@code status} is only honoured for technicians (DRAFT = my drafts) and admins. */
    public record ArticleFilter(String search, Long categoryId, ArticleStatus status, ArticleSort sort) {
    }

    /** {@code version} is required to update (optimistic locking), ignored on creation. */
    public record ArticleRequest(
            @NotBlank(message = "Le titre est obligatoire.")
            @Size(min = 5, max = 150, message = "Le titre doit contenir entre 5 et 150 caractères.") String title,
            @NotBlank(message = "Le résumé est obligatoire.")
            @Size(max = 500, message = "Le résumé ne peut pas dépasser 500 caractères.") String summary,
            @NotBlank(message = "Le contenu est obligatoire.")
            @Size(min = 20, max = 20000, message = "Le contenu doit contenir entre 20 et 20000 caractères.")
            String content,
            @NotNull(message = "La catégorie est obligatoire.") Long categoryId,
            Integer version) {
    }

    public record TicketLinksRequest(
            @NotNull @Size(max = 10, message = "10 articles au maximum par ticket.") List<@NotNull Long> articleIds) {
    }
}
