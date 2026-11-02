package com.nexadesk.ticket;

import com.nexadesk.category.CategoryMapper;
import com.nexadesk.category.Priority;
import com.nexadesk.category.TicketSubCategory;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentRefDto;
import com.nexadesk.ticket.attachment.TicketAttachment;
import com.nexadesk.ticket.comment.TicketComment;
import com.nexadesk.ticket.dto.TicketDtos.AttachmentDto;
import com.nexadesk.ticket.dto.TicketDtos.CommentDto;
import com.nexadesk.ticket.dto.TicketDtos.FeedbackDto;
import com.nexadesk.ticket.dto.TicketDtos.HistoryDto;
import com.nexadesk.ticket.dto.TicketDtos.PriorityRefDto;
import com.nexadesk.ticket.dto.TicketDtos.SubCategoryRefDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import com.nexadesk.ticket.feedback.TicketFeedback;
import com.nexadesk.ticket.history.TicketHistory;
import com.nexadesk.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hand-written mapper: the detail view combines the entity with computed data (actions, feedback). */
@Component
@RequiredArgsConstructor
public class TicketMapper {

    private final CategoryMapper categoryMapper;
    private final TicketSla ticketSla;

    public TicketSummaryDto toSummary(Ticket t) {
        return new TicketSummaryDto(t.getId(), t.getReference(), t.getType(), t.getTitle(), t.getStatus(),
                priority(t.getPriority()), categoryMapper.toRef(t.getCategory()), user(t.getRequester()),
                user(t.getAssignee()), ticketSla.view(t), t.getCreatedAt(), t.getUpdatedAt());
    }

    public TicketDetailDto toDetail(Ticket t, TicketFeedback feedback, Set<TicketAction> actions) {
        return new TicketDetailDto(t.getId(), t.getReference(), t.getType(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getImpact(), t.getUrgency(), priority(t.getPriority()),
                categoryMapper.toRef(t.getCategory()), subcategory(t.getSubcategory()), user(t.getRequester()),
                user(t.getAssignee()), EquipmentRefDto.from(t.getEquipment()), t.getPendingReason(),
                t.getRejectionReason(),
                t.getResolution(),
                t.getResolvedAt(), t.getClosedAt(), t.getReopenCount(), ticketSla.view(t), t.getVersion(),
                t.getCreatedAt(), t.getUpdatedAt(), feedback(feedback), actions);
    }

    public CommentDto toDto(TicketComment c) {
        return new CommentDto(c.getId(), user(c.getAuthor()), c.getBody(), c.isInternal(), c.getCreatedAt());
    }

    public AttachmentDto toDto(TicketAttachment a) {
        return new AttachmentDto(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
                user(a.getUploadedBy()), a.getCreatedAt());
    }

    public HistoryDto toDto(TicketHistory h) {
        return new HistoryDto(h.getId(), user(h.getActor()), h.getAction().name(), h.getField(), h.getOldValue(),
                h.getNewValue(), h.getComment(), h.getCreatedAt());
    }

    public UserRefDto user(User u) {
        return u == null ? null : new UserRefDto(u.getId(), u.getFullName(), u.getAvatarColor());
    }

    private static PriorityRefDto priority(Priority p) {
        return new PriorityRefDto(p.getCode(), p.getLabel(), p.getColor(), p.getRank());
    }

    private static SubCategoryRefDto subcategory(TicketSubCategory s) {
        return s == null ? null : new SubCategoryRefDto(s.getId(), s.getName());
    }

    private static FeedbackDto feedback(TicketFeedback f) {
        return f == null ? null : new FeedbackDto(f.getRating(), f.getComment(), f.getCreatedAt());
    }
}
