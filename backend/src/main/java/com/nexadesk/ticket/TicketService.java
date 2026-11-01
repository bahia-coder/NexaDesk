package com.nexadesk.ticket;

import com.nexadesk.category.Priority;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.category.PriorityMatrix;
import com.nexadesk.category.PriorityRepository;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.category.TicketSubCategory;
import com.nexadesk.category.TicketSubCategoryRepository;
import com.nexadesk.common.web.PageResponse;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.nexadesk.ticket.dto.TicketDtos.ReclassifyRequest;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketFilter;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import com.nexadesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import com.nexadesk.ticket.feedback.TicketFeedback;
import com.nexadesk.ticket.feedback.TicketFeedbackRepository;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket creation, queries and edits. Workflow transitions live in {@link TicketWorkflowService}. */
@Service
public class TicketService {

    /** The board shows at most this many tickets in total (most urgent first). */
    static final int BOARD_LIMIT = 300;

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketSubCategoryRepository subCategoryRepository;
    private final PriorityRepository priorityRepository;
    private final UserRepository userRepository;
    private final TicketFeedbackRepository feedbackRepository;
    private final TicketReferenceGenerator referenceGenerator;
    private final TicketHistoryRecorder history;
    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketMapper mapper;
    private final Clock clock;
    private final ZoneId zone;

    public TicketService(TicketRepository ticketRepository, TicketCategoryRepository categoryRepository,
                         TicketSubCategoryRepository subCategoryRepository, PriorityRepository priorityRepository,
                         UserRepository userRepository, TicketFeedbackRepository feedbackRepository,
                         TicketReferenceGenerator referenceGenerator, TicketHistoryRecorder history,
                         TicketAccess access, TicketPolicy policy, TicketMapper mapper, Clock clock,
                         @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.priorityRepository = priorityRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.referenceGenerator = referenceGenerator;
        this.history = history;
        this.access = access;
        this.policy = policy;
        this.mapper = mapper;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public TicketDetailDto create(CreateTicketRequest request) {
        User actor = access.actor();
        User requester = requesterFor(request.requesterId(), actor);
        TicketCategory category = activeCategory(request.categoryId());
        TicketSubCategory subcategory = activeSubcategory(request.subcategoryId(), category);
        PriorityCode priorityCode = PriorityMatrix.priorityFor(request.impact(), request.urgency());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .reference(referenceGenerator.next(request.type()))
                .type(request.type())
                .title(request.title().trim())
                .description(request.description().trim())
                .impact(request.impact())
                .urgency(request.urgency())
                .priority(priority(priorityCode))
                .category(category)
                .subcategory(subcategory)
                .requester(requester)
                .build());

        String onBehalf = requester == actor ? null : "Créé pour " + requester.getFullName();
        history.change(ticket, actor, HistoryAction.CREATED, "reference", null, ticket.getReference(), onBehalf);
        return detail(ticket, actor);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryDto> list(TicketFilter filter, Pageable pageable) {
        User actor = access.actor();
        return PageResponse.from(ticketRepository.findAll(TicketSpecifications.matching(filter, actor, zone), pageable)
                .map(mapper::toSummary));
    }

    /** Kanban: open and resolved tickets grouped by status, most urgent first within each column. */
    @Transactional(readOnly = true)
    public Map<TicketStatus, List<TicketSummaryDto>> board(TicketFilter filter) {
        User actor = access.actor();
        List<TicketStatus> statuses = filter.status() == null || filter.status().isEmpty()
                ? List.copyOf(TicketStatus.BOARD)
                : filter.status().stream().filter(TicketStatus.BOARD::contains).toList();
        TicketFilter boardFilter = new TicketFilter(filter.search(), statuses, filter.priority(), filter.type(),
                filter.categoryId(), filter.assigneeId(), filter.requesterId(), filter.scope(), filter.from(),
                filter.to());
        Pageable page = PageRequest.of(0, BOARD_LIMIT,
                Sort.by(Sort.Order.desc("priority.rank"), Sort.Order.asc("createdAt")));

        Map<TicketStatus, List<TicketSummaryDto>> columns = new EnumMap<>(TicketStatus.class);
        statuses.stream().sorted().forEach(status -> columns.put(status, new ArrayList<>()));
        ticketRepository.findAll(TicketSpecifications.matching(boardFilter, actor, zone), page)
                .forEach(ticket -> columns.get(ticket.getStatus()).add(mapper.toSummary(ticket)));
        return columns;
    }

    @Transactional(readOnly = true)
    public TicketDetailDto get(Long id) {
        User actor = access.actor();
        return detail(access.visibleTicket(id, actor), actor);
    }

    @Transactional(readOnly = true)
    public TicketDetailDto getByReference(String reference) {
        User actor = access.actor();
        return detail(access.visibleTicket(reference, actor), actor);
    }

    @Transactional
    public TicketDetailDto update(Long id, UpdateTicketRequest request) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.EDIT, ticket, actor, clock.instant());
        if (ticket.getVersion() != request.version()) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        String title = request.title().trim();
        String description = request.description().trim();
        history.change(ticket, actor, HistoryAction.UPDATED, "title", ticket.getTitle(), title, null);
        history.change(ticket, actor, HistoryAction.UPDATED, "description", ticket.getDescription(), description,
                null);
        ticket.setTitle(title);
        ticket.setDescription(description);
        return detailAfterChange(ticket, actor);
    }

    /** Category / priority correction by the handler, with a mandatory, traced reason. */
    @Transactional
    public TicketDetailDto reclassify(Long id, ReclassifyRequest request) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(id, actor);
        policy.require(TicketAction.RECLASSIFY, ticket, actor, clock.instant());
        String reason = request.reason().trim();

        TicketCategory category = ticket.getCategory().getId().equals(request.categoryId())
                ? ticket.getCategory() : activeCategory(request.categoryId());
        TicketSubCategory subcategory = request.subcategoryId() != null && ticket.getSubcategory() != null
                && ticket.getSubcategory().getId().equals(request.subcategoryId())
                ? ticket.getSubcategory() : activeSubcategory(request.subcategoryId(), category);
        Priority priority = priority(request.priority());

        history.change(ticket, actor, HistoryAction.CATEGORY_CHANGED, "category", ticket.getCategory().getName(),
                category.getName(), reason);
        history.change(ticket, actor, HistoryAction.CATEGORY_CHANGED, "subcategory", name(ticket.getSubcategory()),
                name(subcategory), reason);
        history.change(ticket, actor, HistoryAction.PRIORITY_CHANGED, "priority", ticket.getPriority().getCode(),
                priority.getCode(), reason);
        ticket.setCategory(category);
        ticket.setSubcategory(subcategory);
        ticket.setPriority(priority);
        return detailAfterChange(ticket, actor);
    }

    /**
     * Flushes first so the returned {@code version} and {@code updatedAt} are the new ones: a client reusing
     * the response for its next edit must not be rejected as stale.
     */
    TicketDetailDto detailAfterChange(Ticket ticket, User actor) {
        ticketRepository.flush();
        return detail(ticket, actor);
    }

    TicketDetailDto detail(Ticket ticket, User actor) {
        TicketFeedback feedback = feedbackRepository.findByTicket_Id(ticket.getId()).orElse(null);
        Set<TicketAction> actions = policy.availableActions(ticket, actor, clock.instant());
        if (feedback != null) {
            actions.remove(TicketAction.GIVE_FEEDBACK);
        }
        return mapper.toDetail(ticket, feedback, actions);
    }

    private User requesterFor(Long requesterId, User actor) {
        if (requesterId == null || requesterId.equals(actor.getId())) {
            return actor;
        }
        if (actor.getRoleCode() == RoleCode.EMPLOYEE) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Un employé ne peut créer de ticket que pour lui-même.");
        }
        return userRepository.findById(requesterId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", requesterId));
    }

    private TicketCategory activeCategory(Long id) {
        TicketCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE,
                    "La catégorie « " + category.getName() + " » est désactivée.");
        }
        return category;
    }

    private TicketSubCategory activeSubcategory(Long id, TicketCategory category) {
        if (id == null) {
            return null;
        }
        TicketSubCategory subcategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sous-catégorie", id));
        if (!subcategory.getCategory().getId().equals(category.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "La sous-catégorie « " + subcategory.getName() + " » n'appartient pas à « "
                            + category.getName() + " ».");
        }
        if (!subcategory.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE,
                    "La sous-catégorie « " + subcategory.getName() + " » est désactivée.");
        }
        return subcategory;
    }

    private Priority priority(PriorityCode code) {
        return priorityRepository.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("Priorité", code));
    }

    private static String name(TicketSubCategory subcategory) {
        return subcategory == null ? null : subcategory.getName();
    }
}
