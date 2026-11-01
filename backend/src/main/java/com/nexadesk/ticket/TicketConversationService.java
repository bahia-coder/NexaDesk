package com.nexadesk.ticket;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.ticket.comment.TicketComment;
import com.nexadesk.ticket.comment.TicketCommentRepository;
import com.nexadesk.ticket.dto.TicketDtos.CommentDto;
import com.nexadesk.ticket.dto.TicketDtos.CommentRequest;
import com.nexadesk.ticket.dto.TicketDtos.FeedbackDto;
import com.nexadesk.ticket.dto.TicketDtos.FeedbackRequest;
import com.nexadesk.ticket.dto.TicketDtos.HistoryDto;
import com.nexadesk.ticket.feedback.TicketFeedback;
import com.nexadesk.ticket.feedback.TicketFeedbackRepository;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import com.nexadesk.ticket.history.TicketHistoryRepository;
import com.nexadesk.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments, timeline and satisfaction rating of a ticket. */
@Service
@RequiredArgsConstructor
public class TicketConversationService {

    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketCommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketFeedbackRepository feedbackRepository;
    private final TicketHistoryRecorder history;
    private final TicketMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CommentDto> comments(Long ticketId) {
        User actor = access.actor();
        access.visibleTicket(ticketId, actor);
        List<TicketComment> comments = TicketAccess.isStaff(actor)
                ? commentRepository.findByTicket_IdOrderByCreatedAtAscIdAsc(ticketId)
                : commentRepository.findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(ticketId);
        return comments.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public CommentDto addComment(Long ticketId, CommentRequest request) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        Instant now = clock.instant();
        policy.require(request.internal() ? TicketAction.COMMENT_INTERNAL : TicketAction.COMMENT, ticket, actor, now);

        TicketComment comment = commentRepository.save(
                new TicketComment(ticket, actor, request.body().trim(), request.internal(), now));
        if (request.internal()) {
            history.recordInternal(ticket, actor, HistoryAction.COMMENT_ADDED, "Note interne");
        } else {
            history.record(ticket, actor, HistoryAction.COMMENT_ADDED, null);
            resumeIfRequesterAnswered(ticket, actor);
        }
        return mapper.toDto(comment);
    }

    @Transactional(readOnly = true)
    public List<HistoryDto> history(Long ticketId) {
        User actor = access.actor();
        access.visibleTicket(ticketId, actor);
        return (TicketAccess.isStaff(actor)
                ? historyRepository.findByTicket_IdOrderByCreatedAtAscIdAsc(ticketId)
                : historyRepository.findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(ticketId))
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public FeedbackDto giveFeedback(Long ticketId, FeedbackRequest request) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        Instant now = clock.instant();
        policy.require(TicketAction.GIVE_FEEDBACK, ticket, actor, now);
        if (feedbackRepository.existsByTicket_Id(ticketId)) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED, "Ce ticket a déjà été évalué.");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        TicketFeedback feedback = feedbackRepository.save(
                new TicketFeedback(ticket, actor, (short) request.rating(), comment, now));
        history.change(ticket, actor, HistoryAction.FEEDBACK_ADDED, "rating", null, request.rating() + "/5", comment);
        return new FeedbackDto(feedback.getRating(), feedback.getComment(), feedback.getCreatedAt());
    }

    /** A ticket waiting for the requester resumes as soon as they answer. */
    private void resumeIfRequesterAnswered(Ticket ticket, User author) {
        if (ticket.getStatus() == TicketStatus.PENDING && ticket.getPendingReason() == PendingReason.WAITING_USER
                && ticket.isRequestedBy(author.getId())) {
            ticket.setPendingReason(null);
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            history.change(ticket, null, HistoryAction.STATUS_CHANGED, "status", TicketStatus.PENDING,
                    TicketStatus.IN_PROGRESS, "Reprise automatique : réponse du demandeur");
        }
    }
}
