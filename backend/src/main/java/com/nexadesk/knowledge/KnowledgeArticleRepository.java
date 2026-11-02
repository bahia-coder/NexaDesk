package com.nexadesk.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    @EntityGraph(attributePaths = {"category", "author", "sourceTicket"})
    Optional<KnowledgeArticle> findWithDetailsById(Long id);

    /** Rows of a search result page, loaded in one query (the order is restored by the caller). */
    @EntityGraph(attributePaths = {"category", "author"})
    List<KnowledgeArticle> findAllByIdIn(Collection<Long> ids);

    boolean existsBySourceTicket_Id(Long ticketId);

    @Modifying
    @Query(value = "update knowledge_articles set view_count = view_count + 1 where id = :id", nativeQuery = true)
    void incrementViews(@Param("id") Long id);
}
