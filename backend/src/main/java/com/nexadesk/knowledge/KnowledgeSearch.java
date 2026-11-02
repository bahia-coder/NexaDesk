package com.nexadesk.knowledge;

import com.nexadesk.common.TextSearch;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSort;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Native full-text queries on {@code knowledge_articles}. They return ids (and scores); entities are then loaded
 * by the repository. Visibility is part of the SQL: an employee only ever reads published articles, a technician
 * also reads their own drafts, an admin reads everything.
 */
@Repository
public class KnowledgeSearch {

    public record Page(List<Long> ids, long total) {
    }

    public record Match(long id, double score) {
    }

    private static final String VISIBLE = """
             (:admin or a.status = 'PUBLISHED'
                    or (:technician and a.status = 'DRAFT' and a.author_id = :viewerId))
             and (cast(:status as varchar) is null or a.status = :status)
             and (cast(:categoryId as bigint) is null or a.category_id = :categoryId)
             and (:text = '' or a.search_vector @@ %s or a.title %% :text)
            """.formatted(TextSearch.ALL_TERMS);

    private static final Map<ArticleSort, String> ORDER = Map.of(
            ArticleSort.RELEVANCE, "ts_rank(a.search_vector, " + TextSearch.ALL_TERMS
                    + ") + similarity(a.title, :text) desc, a.id desc",
            ArticleSort.RECENT, "a.updated_at desc, a.id desc",
            ArticleSort.POPULAR, "a.helpful_count desc, a.view_count desc, a.id desc");

    private final JdbcClient jdbc;

    public KnowledgeSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of the articles visible to {@code viewer}; {@code text} may be empty (no text filter). */
    public Page search(User viewer, String text, ArticleStatus status, Long categoryId, ArticleSort sort,
                       int page, int size) {
        long total = bind(jdbc.sql("select count(*) from knowledge_articles a where " + VISIBLE),
                viewer, text, status, categoryId).query(Long.class).single();
        List<Long> ids = bind(jdbc.sql("select a.id from knowledge_articles a where " + VISIBLE
                        + " order by " + ORDER.get(sort) + " limit :limit offset :offset"),
                viewer, text, status, categoryId)
                .param("limit", size).param("offset", (long) page * size)
                .query(Long.class).list();
        return new Page(ids, total);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, User viewer, String text,
                                                 ArticleStatus status, Long categoryId) {
        return statement
                .param("admin", viewer.getRoleCode() == RoleCode.ADMIN)
                .param("technician", viewer.getRoleCode() == RoleCode.TECHNICIAN)
                .param("viewerId", viewer.getId())
                .param("text", text)
                .param("status", status == null ? null : status.name())
                .param("categoryId", categoryId);
    }

    /**
     * Published articles related to a free text (a ticket, a question), best first. Used by the AI module for
     * suggestions and as the retrieval step of the assistant.
     */
    public List<Match> related(String text, String title, int limit) {
        if (text.isBlank()) {
            return List.of();
        }
        return jdbc.sql("""
                        with q as (select %s as query)
                        select a.id, ts_rank(a.search_vector, q.query) + 0.3 * similarity(a.title, :title) as score
                        from knowledge_articles a, q
                        where a.status = 'PUBLISHED' and (a.search_vector @@ q.query or a.title %% :title)
                        order by score desc, a.id desc
                        limit :limit""".formatted(TextSearch.ANY_TERM))
                .param("text", text).param("title", title).param("limit", limit)
                .query((rs, i) -> new Match(rs.getLong("id"), rs.getDouble("score")))
                .list();
    }
}
