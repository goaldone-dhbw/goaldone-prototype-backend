package de.goaldone.backend.repository;

import de.goaldone.backend.entity.WorkingHourEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkingHourEntryRepository extends JpaRepository<WorkingHourEntry, UUID> {
    List<WorkingHourEntry> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
