package de.goaldone.backend.repository;

import de.goaldone.backend.entity.RecurringException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringExceptionRepository extends JpaRepository<RecurringException, UUID> {
    Optional<RecurringException> findByTemplateIdAndOccurrenceDate(
        UUID templateId, LocalDate occurrenceDate);

    List<RecurringException> findByTemplateIdInAndOccurrenceDateBetween(
        List<UUID> templateIds, LocalDate from, LocalDate to);

    void deleteByTemplateId(UUID templateId);
}
