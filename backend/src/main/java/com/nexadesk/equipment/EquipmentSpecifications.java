package com.nexadesk.equipment;

import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class EquipmentSpecifications {

    private static final char ESCAPE = '\\';

    private EquipmentSpecifications() {
    }

    static Specification<Equipment> matching(EquipmentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.search() != null && !filter.search().isBlank()) {
                String like = "%" + filter.search().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("inventoryCode")), like, ESCAPE),
                        cb.like(cb.lower(root.get("name")), like, ESCAPE),
                        cb.like(cb.lower(root.get("brand")), like, ESCAPE),
                        cb.like(cb.lower(root.get("model")), like, ESCAPE),
                        cb.like(cb.lower(root.get("serialNumber")), like, ESCAPE),
                        cb.like(cb.lower(root.get("location")), like, ESCAPE)));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.assignedUserId() != null) {
                predicates.add(cb.equal(root.get("assignedUser").get("id"), filter.assignedUserId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
