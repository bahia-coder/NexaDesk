package com.nexadesk.ticket;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Every reference shown in a list row is fetched with the page: no N+1 queries. */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"priority", "category", "requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"priority", "category", "subcategory", "requester", "assignee"})
    Optional<Ticket> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"priority", "category", "subcategory", "requester", "assignee"})
    Optional<Ticket> findWithDetailsByReference(String reference);

    long countByCategory_IdAndStatusIn(Long categoryId, Collection<TicketStatus> statuses);

    List<Ticket> findTop10ByAssignee_IdAndStatusInOrderByCreatedAtAsc(Long assigneeId,
                                                                       Collection<TicketStatus> statuses);

    /** Open tickets per technician: [assigneeId, count]. */
    @Query("""
            select t.assignee.id, count(t) from Ticket t
            where t.status in :statuses and t.assignee is not null
            group by t.assignee.id""")
    List<Object[]> countByAssignee(@Param("statuses") Collection<TicketStatus> statuses);
}
