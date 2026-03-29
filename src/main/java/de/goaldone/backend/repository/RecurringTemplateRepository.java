package de.goaldone.backend.repository;

import de.goaldone.backend.entity.RecurringTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, UUID> {
    Page<RecurringTemplate> findByOwnerIdAndOrganizationId(
        UUID ownerId, UUID organizationId, Pageable pageable);

    List<RecurringTemplate> findByOwnerIdAndOrganizationId(
        UUID ownerId, UUID organizationId);

    Optional<RecurringTemplate> findByIdAndOwnerIdAndOrganizationId(
        UUID id, UUID ownerId, UUID organizationId);
}
