package com.nexadesk.ticket;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.ticket.dto.TicketDtos.TicketFilter;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * List filters + row-level visibility. Visibility is part of the query itself (not a filter applied after
 * loading), so pagination counts are right and a ticket the user may not see is never even read.
 * Mirrors {@link TicketPolicy#canView}.
 */
final class TicketSpecifications {

    private static final char ESCAPE = '\\';

    private TicketSpecifications() {
    }

    static Specification<Ticket> matching(TicketFilter filter, User viewer, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(visibleTo(viewer, root, cb));

            if (filter.search() != null && !filter.search().isBlank()) {
                String like = "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), like, ESCAPE),
                        cb.like(cb.lower(root.get("title")), like, ESCAPE),
                        cb.like(cb.lower(root.get("description")), like, ESCAPE)));
            }
            if (notEmpty(filter.status())) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (notEmpty(filter.priority())) {
                predicates.add(root.get("priority").get("code").in(filter.priority()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (filter.requesterId() != null) {
                predicates.add(cb.equal(root.get("requester").get("id"), filter.requesterId()));
            }
            if (filter.scope() != null) {
                switch (filter.scope()) {
                    case MINE -> predicates.add(cb.equal(root.get("requester").get("id"), viewer.getId()));
                    case ASSIGNED_TO_ME -> predicates.add(cb.equal(root.get("assignee").get("id"), viewer.getId()));
                    case UNASSIGNED -> predicates.add(cb.isNull(root.get("assignee")));
                    case ALL -> { }
                }
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        filter.from().atStartOfDay(zone).toInstant()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate visibleTo(User viewer, Root<Ticket> root, CriteriaBuilder cb) {
        if (viewer.getRoleCode() == RoleCode.ADMIN) {
            return cb.conjunction();
        }
        Predicate requester = cb.equal(root.get("requester").get("id"), viewer.getId());
        if (viewer.getRoleCode() != RoleCode.TECHNICIAN) {
            return requester;
        }
        Predicate assignee = cb.equal(root.get("assignee").get("id"), viewer.getId());
        List<Long> specialtyIds = viewer.getSpecialties().stream().map(TicketCategory::getId).toList();
        if (specialtyIds.isEmpty()) {
            return cb.or(requester, assignee);
        }
        return cb.or(requester, assignee, root.get("category").get("id").in(specialtyIds));
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
