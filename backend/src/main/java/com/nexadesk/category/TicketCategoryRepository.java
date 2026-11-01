package com.nexadesk.category;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    /** Small reference list: categories and their subcategories in one query. */
    @EntityGraph(attributePaths = "subcategories")
    List<TicketCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<TicketCategory> findAllByIdIn(List<Long> ids);
}
