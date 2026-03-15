package de.goaldone.backend.repository;

import de.goaldone.backend.entity.Invitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    boolean existsByEmailAndOrganizationId(String email, UUID organizationId);
    Page<Invitation> findByOrganizationId(UUID organizationId, Pageable pageable);
}
