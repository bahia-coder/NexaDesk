package com.nexadesk.sla;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlaConfigurationRepository extends JpaRepository<SlaConfiguration, Long> {

    @Query("select c from SlaConfiguration c join fetch c.priority p order by p.rank")
    List<SlaConfiguration> findAllWithPriority();
}
