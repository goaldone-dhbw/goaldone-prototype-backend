package de.goaldone.backend.repository;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByOrganizationId(UUID organizationId, Pageable pageable);
    Page<User> findAllByRole(Role role, Pageable pageable);
    long countByOrganizationIdAndRole(UUID organizationId, Role role);
}
