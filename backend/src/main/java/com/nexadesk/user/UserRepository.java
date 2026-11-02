package com.nexadesk.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findWithRoleById(Long id);

    /** Fetches role and department with each page to avoid N+1 queries in the list. */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"role", "department"})
    Page<User> findAll(Specification<User> spec, @NonNull Pageable pageable);

    @Query("""
            select distinct u from User u
            left join fetch u.specialties
            where u.role.code = com.nexadesk.user.RoleCode.TECHNICIAN and u.active = true
            order by u.lastName, u.firstName""")
    List<User> findActiveTechnicians();

    @Query("""
            select distinct u from User u
            left join fetch u.specialties
            where u.role.code = com.nexadesk.user.RoleCode.TECHNICIAN and u.active = true
              and exists (select 1 from User t join t.specialties s where t = u and s.id = :categoryId)
            order by u.lastName, u.firstName""")
    List<User> findActiveTechniciansWithSpecialty(@Param("categoryId") Long categoryId);

    List<User> findByRole_CodeAndActiveTrue(RoleCode code);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRole_CodeAndActiveTrue(RoleCode code);
}
