package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.BreakResponse;
import de.goaldone.backend.model.CreateBreakRequest;
import de.goaldone.backend.model.RecurrenceRule;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    public BreakResponse createBreak(CreateBreakRequest request, UUID userId) {
        validateBreakRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Break newBreak = Break.builder()
                .label(request.getLabel())
                .startTime(LocalTime.parse(request.getStartTime()))
                .endTime(LocalTime.parse(request.getEndTime()))
                .recurrenceType(RecurrenceType.valueOf(request.getRecurrence().getType().getValue()))
                .recurrenceInterval(request.getRecurrence().getInterval())
                .user(user)
                .build();

        newBreak = breakRepository.save(newBreak);

        return mapToBreakResponse(newBreak);
    }

    @Transactional
    public BreakResponse updateBreak(UUID breakId, CreateBreakRequest request, UUID userId) {
        validateBreakRequest(request);

        Break existingBreak = breakRepository.findById(breakId)
                .orElseThrow(() -> new ResourceNotFoundException("Break not found"));

        if (!existingBreak.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this break");
        }

        existingBreak.setLabel(request.getLabel());
        existingBreak.setStartTime(LocalTime.parse(request.getStartTime()));
        existingBreak.setEndTime(LocalTime.parse(request.getEndTime()));
        existingBreak.setRecurrenceType(RecurrenceType.valueOf(request.getRecurrence().getType().getValue()));
        existingBreak.setRecurrenceInterval(request.getRecurrence().getInterval());

        existingBreak = breakRepository.save(existingBreak);

        return mapToBreakResponse(existingBreak);
    }

    private void validateBreakRequest(CreateBreakRequest request) {
        validationService.requireNotBlank(request.getLabel(), "label");
        validationService.requireMaxLength(request.getLabel(), "label", 255);
        validationService.requireNotBlank(request.getStartTime(), "startTime");
        validationService.requireNotBlank(request.getEndTime(), "endTime");

        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());
        validationService.requireAfter(end, start, "endTime");

        validationService.requireNotNull(request.getRecurrence(), "recurrence");
        validationService.requireNotNull(request.getRecurrence().getType(), "recurrence.type");
        validationService.requirePositive(request.getRecurrence().getInterval(), "recurrence.interval");
    }

    @Transactional
    public void deleteBreak(UUID breakId, UUID userId) {
        Break existingBreak = breakRepository.findById(breakId)
                .orElseThrow(() -> new ResourceNotFoundException("Break not found"));

        if (!existingBreak.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this break");
        }

        breakRepository.delete(existingBreak);
    }

    private BreakResponse mapToBreakResponse(Break entity) {
        RecurrenceRule recurrenceRule = RecurrenceRule.builder()
                .type(de.goaldone.backend.model.RecurrenceType.fromValue(entity.getRecurrenceType().name()))
                .interval(entity.getRecurrenceInterval())
                .build();

        return BreakResponse.builder()
                .id(entity.getId())
                .label(entity.getLabel())
                .startTime(entity.getStartTime().toString())
                .endTime(entity.getEndTime().toString())
                .recurrence(recurrenceRule)
                .build();
    }
}

