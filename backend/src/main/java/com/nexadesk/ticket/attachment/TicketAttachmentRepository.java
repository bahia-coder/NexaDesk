package com.nexadesk.ticket.attachment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<TicketAttachment> findByTicket_IdOrderByCreatedAtAsc(Long ticketId);

    @EntityGraph(attributePaths = "uploadedBy")
    Optional<TicketAttachment> findByIdAndTicket_Id(Long id, Long ticketId);

    long countByTicket_Id(Long ticketId);
}
