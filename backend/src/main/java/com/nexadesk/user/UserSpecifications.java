package com.nexadesk.user;

import com.nexadesk.user.dto.UserFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Translates the combinable list filters into a single JPA Criteria predicate. */
final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    static Specification<User> matching(UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.search() != null && !filter.search().isBlank()) {
                String like = "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like, ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), like, ESCAPE),
                        cb.like(cb.lower(root.get("email")), like, ESCAPE),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))),
                                like, ESCAPE)));
            }
            if (filter.role() != null) {
                predicates.add(cb.equal(root.get("role").get("code"), filter.role()));
            }
            if (filter.active() != null) {
                predicates.add(cb.equal(root.get("active"), filter.active()));
            }
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), filter.departmentId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** User input must not be able to inject LIKE wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
