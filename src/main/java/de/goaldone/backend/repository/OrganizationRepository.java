package de.goaldone.backend.repository;

import de.goaldone.backend.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByAllowedDomain(String allowedDomain);
    boolean existsByName(String name);
}
