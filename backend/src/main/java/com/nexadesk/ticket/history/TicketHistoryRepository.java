package com.nexadesk.ticket.history;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    @EntityGraph(attributePaths = "actor")
    List<TicketHistory> findByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);

    @EntityGraph(attributePaths = "actor")
    List<TicketHistory> findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);
}
