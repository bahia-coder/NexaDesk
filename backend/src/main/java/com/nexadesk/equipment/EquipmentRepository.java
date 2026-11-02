package com.nexadesk.equipment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = "assignedUser")
    Page<Equipment> findAll(Specification<Equipment> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = "assignedUser")
    Optional<Equipment> findWithUserById(Long id);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, Long id);

    /** What an employee may attach to a ticket: their own devices and shared deployed ones. */
    @EntityGraph(attributePaths = "assignedUser")
    @Query("""
            select e from Equipment e
            where e.status <> com.nexadesk.equipment.EquipmentStatus.RETIRED
              and (e.assignedUser.id = :userId
                   or (e.assignedUser is null and e.status = com.nexadesk.equipment.EquipmentStatus.IN_USE))
            order by e.inventoryCode""")
    List<Equipment> findSelectableBy(Long userId);

    @EntityGraph(attributePaths = "assignedUser")
    @Query("""
            select e from Equipment e
            where e.status <> com.nexadesk.equipment.EquipmentStatus.RETIRED
            order by e.inventoryCode""")
    List<Equipment> findAllNotRetired();
}
