package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.BreakResponse;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.CreateBreakRequest;
import de.goaldone.backend.model.RecurrenceRule;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BreakService {

    private final BreakRepository breakRepository;
    private final UserRepository userRepository;
    private final ValidationService validationService;

    @Transactional(readOnly = true)
    public List<BreakResponse> listBreaks(UUID userId) {
        return breakRepository.findByUserId(userId).stream()
                .map(this::mapToBreakResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BreakResponse createBreak(CreateBreakRequest request, UUID userId, UUID organizationId) {
        validateBreakRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Break newBreak = Break.builder()
                .label(request.getLabel())
                .startTime(LocalTime.parse(request.getStartTime()))
                .endTime(LocalTime.parse(request.getEndTime()))
                .breakType(request.getBreakType())
                .user(user)
                .organizationId(organizationId)
                .build();

        // Set type-specific fields
        setBreakTypeSpecificFields(newBreak, request);

        newBreak = breakRepository.save(newBreak);

        return mapToBreakResponse(newBreak);
    }

    @Transactional
    public BreakResponse updateBreak(UUID breakId, CreateBreakRequest request, UUID userId, UUID organizationId) {
        validateBreakRequest(request);

        Break existingBreak = breakRepository.findById(breakId)
                .orElseThrow(() -> new ResourceNotFoundException("Break not found"));

        if (!existingBreak.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this break");
        }

        if (!existingBreak.getOrganizationId().equals(organizationId)) {
            throw new AccessDeniedException("Access denied: Break belongs to a different organization");
        }

        existingBreak.setLabel(request.getLabel());
        existingBreak.setStartTime(LocalTime.parse(request.getStartTime()));
        existingBreak.setEndTime(LocalTime.parse(request.getEndTime()));
        existingBreak.setBreakType(request.getBreakType());

        // Clear all type-specific fields first
        clearBreakTypeSpecificFields(existingBreak);

        // Set type-specific fields based on new request
        setBreakTypeSpecificFields(existingBreak, request);

        existingBreak = breakRepository.save(existingBreak);

        return mapToBreakResponse(existingBreak);
    }

    @Transactional
    public void deleteBreak(UUID breakId, UUID userId, UUID organizationId) {
        Break existingBreak = breakRepository.findById(breakId)
                .orElseThrow(() -> new ResourceNotFoundException("Break not found"));

        if (!existingBreak.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this break");
        }

        if (!existingBreak.getOrganizationId().equals(organizationId)) {
            throw new AccessDeniedException("Access denied: Break belongs to a different organization");
        }

        breakRepository.delete(existingBreak);
    }

    private void validateBreakRequest(CreateBreakRequest request) {
        validationService.requireNotBlank(request.getLabel(), "label");
        validationService.requireMaxLength(request.getLabel(), "label", 255);
        validationService.requireNotBlank(request.getStartTime(), "startTime");
        validationService.requireNotBlank(request.getEndTime(), "endTime");

        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());
        validationService.requireAfter(end, start, "endTime");

        validationService.requireNotNull(request.getBreakType(), "breakType");

        // Extract nullable fields from JsonNullable wrappers
        LocalDate date = (request.getDate() != null && request.getDate().isPresent())
            ? request.getDate().get()
            : null;
        LocalDate validFrom = (request.getValidFrom() != null && request.getValidFrom().isPresent())
            ? request.getValidFrom().get()
            : null;
        LocalDate validUntil = (request.getValidUntil() != null && request.getValidUntil().isPresent())
            ? request.getValidUntil().get()
            : null;

        // Validate type-specific constraints
        switch (request.getBreakType()) {
            case ONE_TIME:
                if (date == null) {
                    throw new ValidationException("missing-date", "date is required for ONE_TIME break type");
                }
                if (request.getRecurrence() != null) {
                    throw new ValidationException("unexpected-recurrence", "recurrence must be null for ONE_TIME break type");
                }
                if (validFrom != null || validUntil != null) {
                    throw new ValidationException("unexpected-date-range", "validFrom and validUntil must be null for ONE_TIME break type");
                }
                break;

            case RECURRING:
                if (request.getRecurrence() == null) {
                    throw new ValidationException("missing-recurrence", "recurrence is required for RECURRING break type");
                }
                validationService.requireNotNull(request.getRecurrence().getType(), "recurrence.type");
                validationService.requirePositive(request.getRecurrence().getInterval(), "recurrence.interval");
                if (date != null) {
                    throw new ValidationException("unexpected-date", "date must be null for RECURRING break type");
                }
                if (validFrom != null || validUntil != null) {
                    throw new ValidationException("unexpected-date-range", "validFrom and validUntil must be null for RECURRING break type");
                }
                break;

            case BOUNDED_RECURRING:
                if (request.getRecurrence() == null) {
                    throw new ValidationException("missing-recurrence", "recurrence is required for BOUNDED_RECURRING break type");
                }
                validationService.requireNotNull(request.getRecurrence().getType(), "recurrence.type");
                validationService.requirePositive(request.getRecurrence().getInterval(), "recurrence.interval");

                if (validFrom == null) {
                    throw new ValidationException("missing-valid-from", "validFrom is required for BOUNDED_RECURRING break type");
                }
                if (validUntil == null) {
                    throw new ValidationException("missing-valid-until", "validUntil is required for BOUNDED_RECURRING break type");
                }
                if (validFrom.isAfter(validUntil)) {
                    throw new ValidationException("invalid-date-range", "validFrom must not be after validUntil");
                }
                if (date != null) {
                    throw new ValidationException("unexpected-date", "date must be null for BOUNDED_RECURRING break type");
                }
                break;
        }
    }

    private void setBreakTypeSpecificFields(Break entity, CreateBreakRequest request) {
        switch (request.getBreakType()) {
            case ONE_TIME:
                if (request.getDate() != null && request.getDate().isPresent()) {
                    entity.setDate(request.getDate().get());
                } else {
                    entity.setDate(null);
                }
                entity.setRecurrenceType(null);
                entity.setRecurrenceInterval(null);
                entity.setValidFrom(null);
                entity.setValidUntil(null);
                break;

            case RECURRING:
                entity.setRecurrenceType(RecurrenceType.valueOf(request.getRecurrence().getType().getValue()));
                entity.setRecurrenceInterval(request.getRecurrence().getInterval());
                entity.setDate(null);
                entity.setValidFrom(null);
                entity.setValidUntil(null);
                break;

            case BOUNDED_RECURRING:
                entity.setRecurrenceType(RecurrenceType.valueOf(request.getRecurrence().getType().getValue()));
                entity.setRecurrenceInterval(request.getRecurrence().getInterval());
                if (request.getValidFrom() != null && request.getValidFrom().isPresent()) {
                    entity.setValidFrom(request.getValidFrom().get());
                }
                if (request.getValidUntil() != null && request.getValidUntil().isPresent()) {
                    entity.setValidUntil(request.getValidUntil().get());
                }
                entity.setDate(null);
                break;
        }
    }

    private void clearBreakTypeSpecificFields(Break entity) {
        entity.setDate(null);
        entity.setRecurrenceType(null);
        entity.setRecurrenceInterval(null);
        entity.setValidFrom(null);
        entity.setValidUntil(null);
    }

    /**
     * Determines if this break acts as a blocker on the given day.
     * The logic depends on the breakType:
     * - ONE_TIME: blocks if day equals the break's date
     * - RECURRING: blocks if the day matches the recurrence rule
     * - BOUNDED_RECURRING: blocks if day is within [validFrom, validUntil]
     *   AND the day matches the recurrence rule
     */
    public static boolean breaksBlocksDay(Break b, LocalDate day) {
        return switch (b.getBreakType()) {
            case ONE_TIME ->
                day.equals(b.getDate());
            case RECURRING ->
                recurrenceMatchesDay(b.getRecurrenceType(), b.getRecurrenceInterval(), day);
            case BOUNDED_RECURRING ->
                !day.isBefore(b.getValidFrom())
                && !day.isAfter(b.getValidUntil())
                && recurrenceMatchesDay(b.getRecurrenceType(), b.getRecurrenceInterval(), day);
        };
    }

    /**
     * Helper to check if a recurrence rule matches a given day.
     */
    private static boolean recurrenceMatchesDay(RecurrenceType type, Integer interval, LocalDate day) {
        if (type == null || interval == null) {
            return false;
        }

        return switch (type) {
            case DAILY -> {
                // For simplicity: all daily intervals match.
                // A more complex implementation could track a start date,
                // but for MVP, daily means "every interval days" without a reference point.
                yield true;
            }
            case WEEKLY -> {
                // WEEKLY matches if the interval matches (placeholder for MVP).
                // In a full iCal implementation, you'd check day-of-week.
                yield true;
            }
            case MONTHLY -> {
                // MONTHLY matches if the interval matches (placeholder for MVP).
                yield true;
            }
        };
    }

    private BreakResponse mapToBreakResponse(Break entity) {
        BreakResponse response = BreakResponse.builder()
                .id(entity.getId())
                .label(entity.getLabel())
                .startTime(entity.getStartTime().toString())
                .endTime(entity.getEndTime().toString())
                .breakType(entity.getBreakType())
                .build();

        // Set type-specific fields in response
        switch (entity.getBreakType()) {
            case ONE_TIME:
                if (entity.getDate() != null) {
                    response.setDate(JsonNullable.of(entity.getDate()));
                }
                break;
            case RECURRING:
                if (entity.getRecurrenceType() != null && entity.getRecurrenceInterval() != null) {
                    RecurrenceRule rule = RecurrenceRule.builder()
                            .type(de.goaldone.backend.model.RecurrenceType.fromValue(entity.getRecurrenceType().name()))
                            .interval(entity.getRecurrenceInterval())
                            .build();
                    response.setRecurrence(rule);
                }
                break;
            case BOUNDED_RECURRING:
                if (entity.getRecurrenceType() != null && entity.getRecurrenceInterval() != null) {
                    RecurrenceRule rule = RecurrenceRule.builder()
                            .type(de.goaldone.backend.model.RecurrenceType.fromValue(entity.getRecurrenceType().name()))
                            .interval(entity.getRecurrenceInterval())
                            .build();
                    response.setRecurrence(rule);
                }
                if (entity.getValidFrom() != null) {
                    response.setValidFrom(JsonNullable.of(entity.getValidFrom()));
                }
                if (entity.getValidUntil() != null) {
                    response.setValidUntil(JsonNullable.of(entity.getValidUntil()));
                }
                break;
        }

        return response;
    }
}

