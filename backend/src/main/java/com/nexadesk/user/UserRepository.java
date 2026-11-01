package com.nexadesk.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findWithRoleById(Long id);

    boolean existsByEmail(String email);
}
