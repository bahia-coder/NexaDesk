package com.nexadesk.equipment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, Long> {

    @EntityGraph(attributePaths = "user")
    List<EquipmentAssignment> findByEquipment_IdOrderByAssignedAtDescIdDesc(Long equipmentId);

    Optional<EquipmentAssignment> findByEquipment_IdAndReturnedAtIsNull(Long equipmentId);
}
