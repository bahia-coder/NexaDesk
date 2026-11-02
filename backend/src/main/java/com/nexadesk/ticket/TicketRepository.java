package com.nexadesk.ticket;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Every reference shown in a list row is fetched with the page: no N+1 queries. */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"priority", "category", "requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"priority", "category", "subcategory", "requester", "assignee", "equipment"})
    Optional<Ticket> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"priority", "category", "subcategory", "requester", "assignee", "equipment"})
    Optional<Ticket> findWithDetailsByReference(String reference);

    /** Tickets the SLA monitor must look at: running clock, deadline not yet reported as breached. */
    @EntityGraph(attributePaths = "priority")
    @Query("""
            select t from Ticket t
            where t.status in :statuses and t.resolutionDueAt is not null and t.slaBreachSentAt is null""")
    List<Ticket> findSlaWatchList(@Param("statuses") Collection<TicketStatus> statuses);

    /** Alert flags are set with targeted updates: no version increment, so no conflict with a user's edit. */
    @Modifying
    @Query("update Ticket t set t.slaWarningSentAt = :now where t.id = :id and t.slaWarningSentAt is null")
    int markSlaWarning(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Ticket t set t.slaBreachSentAt = :now, t.slaWarningSentAt = coalesce(t.slaWarningSentAt, :now)
            where t.id = :id and t.slaBreachSentAt is null""")
    int markSlaBreach(@Param("id") Long id, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"priority", "assignee"})
    List<Ticket> findByStatusAndResolvedAtBefore(TicketStatus status, Instant resolvedBefore);

    boolean existsByTitle(String title);

    /** Incident history of a device. */
    @EntityGraph(attributePaths = {"priority", "category", "requester", "assignee"})
    List<Ticket> findByEquipment_IdOrderByCreatedAtDesc(Long equipmentId);

    @EntityGraph(attributePaths = {"priority", "category", "requester", "assignee"})
    List<Ticket> findByCreatedAtAfterOrderByCreatedAtAsc(Instant createdAfter);

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
