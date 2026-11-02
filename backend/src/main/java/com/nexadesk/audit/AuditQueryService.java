package com.nexadesk.audit;

import com.nexadesk.audit.dto.AuditDtos.AuditFilter;
import com.nexadesk.audit.dto.AuditDtos.AuditLogDto;
import com.nexadesk.common.web.PageResponse;
import com.nexadesk.ticket.TicketMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final TicketMapper ticketMapper;
    private final ZoneId zone;

    public AuditQueryService(AuditLogRepository repository, TicketMapper ticketMapper,
                             @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.repository = repository;
        this.ticketMapper = ticketMapper;
        this.zone = zone;
    }

    /** Most recent first; the order is fixed (an audit trail is read chronologically). */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(AuditFilter filter, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(repository.findAll(matching(filter), page).map(this::toDto));
    }

    private Specification<AuditLog> matching(AuditFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.actorId() != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), f.actorId()));
            }
            if (f.action() != null) {
                predicates.add(cb.equal(root.get("action"), f.action()));
            }
            if (f.entityType() != null && !f.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), f.entityType().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.entityId() != null && !f.entityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), f.entityId().trim()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(zone).toInstant()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getId(), ticketMapper.user(a.getActor()), a.getActorEmail(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getSummary(), a.getOldValue(), a.getNewValue(),
                a.getIpAddress(), a.getTraceId(), a.getCreatedAt());
    }
}
