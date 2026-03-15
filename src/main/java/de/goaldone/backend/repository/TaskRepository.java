package de.goaldone.backend.repository;

import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByOwnerIdAndOrganizationId(UUID ownerId, UUID organizationId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE " +
            "t.owner.id = :ownerId AND " +
            "t.organization.id = :organizationId AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:from IS NULL OR t.deadline >= :from) AND " +
            "(:to IS NULL OR t.deadline <= :to)")
    Page<Task> findAllByFilters(
            @Param("ownerId") UUID ownerId,
            @Param("organizationId") UUID organizationId,
            @Param("status") TaskStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );
}
