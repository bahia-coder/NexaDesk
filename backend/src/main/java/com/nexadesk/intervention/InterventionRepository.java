package com.nexadesk.intervention;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    @EntityGraph(attributePaths = {"technician", "ticket"})
    List<Intervention> findByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);

    @EntityGraph(attributePaths = {"technician", "ticket"})
    Optional<Intervention> findByIdAndTicket_Id(Long id, Long ticketId);

    /** Most recent intervention carrying a solution: reused when a ticket is resolved without retyping it. */
    Optional<Intervention> findFirstByTicket_IdAndSolutionIsNotNullOrderByCreatedAtDescIdDesc(Long ticketId);

    @EntityGraph(attributePaths = {"technician", "ticket"})
    @Query("""
            select i from Intervention i
            where i.technician.id = :technicianId and i.createdAt >= :from and i.createdAt < :to""")
    Page<Intervention> findByTechnicianBetween(@Param("technicianId") Long technicianId,
                                               @Param("from") Instant from, @Param("to") Instant to,
                                               Pageable pageable);

    @Query("""
            select coalesce(sum(i.timeSpentMinutes), 0) from Intervention i
            where i.technician.id = :technicianId and i.createdAt >= :from and i.createdAt < :to""")
    long sumMinutesByTechnicianBetween(@Param("technicianId") Long technicianId,
                                       @Param("from") Instant from, @Param("to") Instant to);
}
