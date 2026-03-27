package de.goaldone.backend.repository;

import de.goaldone.backend.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, UUID> {
    List<ScheduleEntry> findByUserIdAndEntryDateBetween(UUID userId, LocalDate start, LocalDate end);
    void deleteByUserIdAndEntryDateBetweenAndIsCompletedFalseAndIsPinnedFalse(UUID userId, LocalDate start, LocalDate end);
    void deleteByUserIdAndEntryDateGreaterThanEqualAndIsCompletedFalseAndIsPinnedFalse(UUID userId, LocalDate startDate);
    Optional<ScheduleEntry> findByIdAndUserId(UUID id, UUID userId);
    List<ScheduleEntry> findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(UUID userId, LocalDate startDate);
    List<ScheduleEntry> findByUserIdAndEntryDateGreaterThanEqualAndIsPinnedTrue(UUID userId, LocalDate startDate);
}
