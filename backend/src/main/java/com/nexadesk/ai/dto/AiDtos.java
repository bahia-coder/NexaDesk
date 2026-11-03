package com.nexadesk.ai.dto;

import com.nexadesk.ai.AiSource;
import com.nexadesk.category.Level;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSummaryDto;
import com.nexadesk.ticket.dto.TicketDtos.SubCategoryRefDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * AI API records. Every generated answer carries its {@link AiSource} and a disclaimer, so the UI can always say
 * whether a model was involved.
 */
public final class AiDtos {

    private AiDtos() {
    }

    /** {@code provider} and {@code model} are null unless a model is connected. */
    public record AiStatusDto(AiSource mode, String provider, String model, String disclaimer,
                              int requestsPerMinute) {
    }

    public record TextRequest(
            @NotBlank(message = "Le titre est obligatoire.") @Size(max = 150) String title,
            @Size(max = 5000) String description) {
    }

    /** {@code category} is null when nothing could be inferred; {@code confidence} is between 0 and 1. */
    public record ClassificationDto(AiSource source, String disclaimer, CategoryRefDto category,
                                    SubCategoryRefDto subcategory, Level impact, Level urgency,
                                    PriorityCode priority, double confidence, List<String> reasons) {
    }

    public record SummaryDto(AiSource source, String disclaimer, String summary, List<String> keyPoints) {
    }

    /** {@code score}: relevance from 1 to 100, relative to the other results. */
    public record SimilarTicketDto(TicketSummaryDto ticket, int score, String resolution) {
    }

    public record ArticleMatchDto(ArticleSummaryDto article, int score) {
    }

    /** Similar content is found by PostgreSQL search, with or without a model: no source field. */
    public record SimilarDto(List<SimilarTicketDto> tickets, List<ArticleMatchDto> articles) {
    }

    /** {@code steps} are only written by a model; the heuristic returns the raw sources with an empty list. */
    public record SuggestionsDto(AiSource source, String disclaimer, List<String> steps,
                                 List<SimilarTicketDto> tickets, List<ArticleMatchDto> articles) {
    }

    public enum ChatRole { USER, ASSISTANT }

    public record ChatTurn(@NotNull ChatRole role, @NotBlank @Size(max = 4000) String content) {
    }

    public record AssistantRequest(
            @NotBlank(message = "La question est vide.") @Size(max = 1000) String question,
            @Size(max = 10, message = "10 messages d'historique au maximum.") List<@Valid @NotNull ChatTurn> history) {
    }

    /**
     * Where an assistant answer comes from, decided by the server from what was actually used.
     * <ul>
     *   <li>{@code KNOWLEDGE_BASE}: internal articles only (written by the model, or listed without one).</li>
     *   <li>{@code HYBRID}: internal articles, completed by general advice kept in a separate part.</li>
     *   <li>{@code AI_FALLBACK}: no article fits; the model's general IT knowledge, never shown with sources.</li>
     *   <li>{@code NO_ANSWER}: no article fits and no model is connected to fall back on.</li>
     * </ul>
     */
    public enum ResponseMode { KNOWLEDGE_BASE, HYBRID, AI_FALLBACK, NO_ANSWER }

    /** How sure the model is that the answer solves the problem; null when no model wrote it. */
    public enum Confidence { LOW, MEDIUM, HIGH }

    /**
     * {@code answer} is null when no model wrote one: the user then gets the matching articles.
     * {@code articles} are the sources actually used, and only those (empty for {@code AI_FALLBACK}).
     * {@code suggestTicket}: the user may need a technician.
     */
    public record AssistantAnswerDto(AiSource source, String disclaimer, ResponseMode responseMode, String answer,
                                     List<ArticleMatchDto> articles, Confidence confidence, boolean suggestTicket) {
    }
}
