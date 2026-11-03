package com.nexadesk.ai;

import com.nexadesk.ai.dto.AiDtos.ArticleMatchDto;
import com.nexadesk.ai.dto.AiDtos.SimilarTicketDto;
import com.nexadesk.common.TextSearch;
import com.nexadesk.knowledge.KnowledgeService;
import com.nexadesk.knowledge.KnowledgeService.RelatedArticle;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketAccess;
import com.nexadesk.ticket.TicketMapper;
import com.nexadesk.ticket.TicketPolicy;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.user.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Similar incidents and related articles, always available (no model needed): PostgreSQL full-text search with
 * French stemming ({@code ts_rank}, title weighted most), plus trigram similarity on titles for typos.
 *
 * <p>Results go through {@link TicketPolicy#canView}: an employee only gets their own past tickets (and the
 * published articles), never a colleague's ticket.
 */
@Service
@RequiredArgsConstructor
public class SimilarityService {

    /** Below this, a match only shares a common word or two with the text: noise rather than help. */
    static final double MIN_SCORE = 0.1;
    /** A long text matches many documents weakly: only those close to the best match are kept. */
    static final double RELATIVE_CUTOFF = 0.5;
    private static final int CANDIDATES = 40;

    private final JdbcClient jdbc;
    private final TicketRepository ticketRepository;
    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketMapper mapper;
    private final KnowledgeService knowledge;

    public record Candidate(Long id, double score) {
    }

    @Transactional(readOnly = true)
    public List<SimilarTicketDto> tickets(String title, String text, Long excludeId, boolean resolvedOnly,
                                          int limit) {
        User viewer = access.actor();
        List<Candidate> candidates = relevant(candidates(title, text, excludeId, resolvedOnly), Candidate::score);
        if (candidates.isEmpty()) {
            return List.of();
        }
        double best = candidates.getFirst().score();
        Map<Long, Ticket> byId = ticketRepository.findAllByIdIn(candidates.stream().map(Candidate::id).toList())
                .stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));
        return candidates.stream()
                .filter(candidate -> byId.containsKey(candidate.id()))
                .filter(candidate -> policy.canView(byId.get(candidate.id()), viewer))
                .limit(limit)
                .map(candidate -> {
                    Ticket ticket = byId.get(candidate.id());
                    return new SimilarTicketDto(mapper.toSummary(ticket), percent(candidate.score(), best),
                            ticket.getResolution());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleMatchDto> articles(String title, String text, int limit) {
        return matches(articlesWithContent(title, text, limit));
    }

    /** Same search, with the article text (for the assistant and the suggestions prompt). */
    @Transactional(readOnly = true)
    public List<RelatedArticle> articlesWithContent(String title, String text, int limit) {
        return relevant(knowledge.related(text, title, limit), RelatedArticle::score);
    }

    /**
     * Assistant candidates: every published article from {@code minScore}, best first, with its text. No cut
     * relative to the best one here: the model judges each candidate itself.
     */
    @Transactional(readOnly = true)
    public List<RelatedArticle> articleCandidates(String title, String text, double minScore, int limit) {
        return knowledge.related(text, title, limit).stream().filter(a -> a.score() >= minScore).toList();
    }

    /** Articles with their relevance (the best one is 100). */
    public static List<ArticleMatchDto> matches(List<RelatedArticle> articles) {
        double best = articles.isEmpty() ? 0 : articles.getFirst().score();
        return articles.stream().map(a -> new ArticleMatchDto(a.article(), percent(a.score(), best))).toList();
    }

    /** Keeps, from a list sorted by decreasing score, the results above both thresholds. */
    static <T> List<T> relevant(List<T> sorted, ToDoubleFunction<T> score) {
        if (sorted.isEmpty()) {
            return List.of();
        }
        double floor = Math.max(MIN_SCORE, RELATIVE_CUTOFF * score.applyAsDouble(sorted.getFirst()));
        return sorted.stream().filter(item -> score.applyAsDouble(item) >= floor).toList();
    }

    List<Candidate> candidates(String title, String text, Long excludeId, boolean resolvedOnly) {
        if (text.isBlank()) {
            return List.of();
        }
        return jdbc.sql("""
                        with q as (select %s as query)
                        select t.id, ts_rank(t.search_vector, q.query) + 0.3 * similarity(t.title, :title) as score
                        from tickets t, q
                        where (t.search_vector @@ q.query or t.title %% :title)
                          and (cast(:excludeId as bigint) is null or t.id <> :excludeId)
                          and (not :resolvedOnly or (t.status in ('RESOLVED', 'CLOSED') and t.resolution is not null))
                        order by score desc, t.id desc
                        limit :limit""".formatted(TextSearch.ANY_TERM))
                .param("text", text).param("title", title).param("excludeId", excludeId)
                .param("resolvedOnly", resolvedOnly).param("limit", CANDIDATES)
                .query((rs, i) -> new Candidate(rs.getLong("id"), rs.getDouble("score")))
                .list();
    }

    /** Relevance shown to users, relative to the best result of the same search (1 to 100). */
    static int percent(double score, double best) {
        return best <= 0 ? 0 : (int) Math.max(1, Math.min(100, Math.round(100 * score / best)));
    }
}
