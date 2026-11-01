package com.nexadesk.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSubCategoryRepository extends JpaRepository<TicketSubCategory, Long> {

    boolean existsByCategory_IdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategory_IdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);
}
