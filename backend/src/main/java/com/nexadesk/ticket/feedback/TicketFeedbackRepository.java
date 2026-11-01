package com.nexadesk.ticket.feedback;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketFeedbackRepository extends JpaRepository<TicketFeedback, Long> {

    Optional<TicketFeedback> findByTicket_Id(Long ticketId);

    boolean existsByTicket_Id(Long ticketId);
}
