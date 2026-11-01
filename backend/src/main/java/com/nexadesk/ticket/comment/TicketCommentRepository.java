package com.nexadesk.ticket.comment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);

    /** Requester view: internal notes are filtered in the query, never sent then hidden by the frontend. */
    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);
}
