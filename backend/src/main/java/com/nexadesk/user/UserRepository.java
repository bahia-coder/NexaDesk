package com.nexadesk.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRole_CodeAndActiveTrue(RoleCode code);
}
