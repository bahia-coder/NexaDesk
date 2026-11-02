package com.nexadesk.search;

import com.nexadesk.equipment.EquipmentService;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentFilter;
import com.nexadesk.knowledge.KnowledgeService;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleFilter;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSort;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSummaryDto;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.ticket.TicketService;
import com.nexadesk.ticket.dto.TicketDtos.TicketFilter;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.UserAdminService;
import com.nexadesk.user.dto.UserDto;
import com.nexadesk.user.dto.UserFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Header search box: a few results of each kind, each list coming from its own service with its own access
 * rules (ticket visibility, published articles for employees). Users and equipment are for admins only.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    static final int PER_KIND = 5;

    private final TicketService tickets;
    private final KnowledgeService knowledge;
    private final UserAdminService users;
    private final EquipmentService equipment;
    private final CurrentUser currentUser;

    public record GlobalSearchDto(List<TicketSummaryDto> tickets, List<ArticleSummaryDto> articles,
                                  List<UserDto> users, List<EquipmentDto> equipment) {
    }

    public GlobalSearchDto search(String query) {
        String q = query.strip();
        List<TicketSummaryDto> ticketHits = tickets.list(
                new TicketFilter(q, null, null, null, null, null, null, null, null, null, null),
                PageRequest.of(0, PER_KIND, Sort.by(Sort.Direction.DESC, "createdAt"))).content();
        List<ArticleSummaryDto> articleHits = knowledge.list(new ArticleFilter(q, null, null, ArticleSort.RELEVANCE),
                PageRequest.of(0, PER_KIND)).content();
        if (!currentUser.hasRole(RoleCode.ADMIN)) {
            return new GlobalSearchDto(ticketHits, articleHits, List.of(), List.of());
        }
        List<UserDto> userHits = users.list(new UserFilter(q, null, null, null),
                PageRequest.of(0, PER_KIND, Sort.by("lastName", "firstName"))).content();
        List<EquipmentDto> equipmentHits = equipment.list(new EquipmentFilter(q, null, null, null),
                PageRequest.of(0, PER_KIND, Sort.by("inventoryCode"))).content();
        return new GlobalSearchDto(ticketHits, articleHits, userHits, equipmentHits);
    }
}
