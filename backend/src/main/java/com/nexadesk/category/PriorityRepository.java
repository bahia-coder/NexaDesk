package com.nexadesk.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityRepository extends JpaRepository<Priority, Long> {

    List<Priority> findAllByOrderByRankAsc();

    Optional<Priority> findByCode(PriorityCode code);
}
