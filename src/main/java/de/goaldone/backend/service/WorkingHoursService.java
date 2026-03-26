package de.goaldone.backend.service;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.WorkingHourEntry;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.UpsertWorkingHoursRequest;
import de.goaldone.backend.model.WorkingHoursDayEntry;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    public static final String ERR_NOT_FOUND = "working-hours-not-found";
    public static final String ERR_SEVEN_DAYS = "working-hours-must-have-seven-days";
    public static final String ERR_DUPLICATE_DAY = "working-hours-duplicate-day";
    public static final String ERR_MISSING_DAY = "working-hours-missing-day";
    public static final String ERR_MISSING_TIME = "working-hours-missing-time-for-work-day";
    public static final String ERR_END_BEFORE_START = "working-hours-end-before-start";

    private final WorkingHourEntryRepository workingHourEntryRepository;
    private final UserRepository userRepository;
    private final ValidationService validationService;

    @Transactional(readOnly = true)
    public WorkingHoursResponse getWorkingHours(UUID userId) {
        List<WorkingHourEntry> entries = workingHourEntryRepository.findByUserId(userId);
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException(ERR_NOT_FOUND);
        }

        return mapToResponse(entries);
    }

    @Transactional
    public WorkingHoursResponse upsertWorkingHours(UpsertWorkingHoursRequest request, UUID userId) {
        validateUpsertRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Atomically replace: Delete old, Insert new
        workingHourEntryRepository.deleteByUserId(userId);

        List<WorkingHourEntry> newEntries = request.getDays().stream()
                .map(dayRequest -> mapToEntity(dayRequest, user))
                .collect(Collectors.toList());

        workingHourEntryRepository.saveAll(newEntries);

        return mapToResponse(newEntries);
    }

    private void validateUpsertRequest(UpsertWorkingHoursRequest request) {
        validationService.requireNotNull(request.getDays(), "days");

        if (request.getDays().size() != 7) {
            throw new ValidationException("days", ERR_SEVEN_DAYS);
        }

        Set<DayOfWeek> daysFound = new HashSet<>();
        for (WorkingHoursDayEntry day : request.getDays()) {
            validationService.requireNotNull(day.getDayOfWeek(), "dayOfWeek");
            DayOfWeek dow = DayOfWeek.valueOf(day.getDayOfWeek().getValue());

            if (!daysFound.add(dow)) {
                throw new ValidationException("dayOfWeek", ERR_DUPLICATE_DAY);
            }

            if (Boolean.TRUE.equals(day.getIsWorkDay())) {
                if (day.getStartTime() == null || day.getStartTime().isBlank()) {
                    throw new ValidationException("startTime", ERR_MISSING_TIME);
                }
                if (day.getEndTime() == null || day.getEndTime().isBlank()) {
                    throw new ValidationException("endTime", ERR_MISSING_TIME);
                }

                LocalTime start = LocalTime.parse(day.getStartTime());
                LocalTime end = LocalTime.parse(day.getEndTime());

                if (!end.isAfter(start)) {
                    throw new ValidationException("endTime", ERR_END_BEFORE_START);
                }
            }
        }

        if (daysFound.size() != 7) {
            throw new ValidationException("days", ERR_MISSING_DAY);
        }
    }

    private WorkingHourEntry mapToEntity(WorkingHoursDayEntry dayRequest, User user) {
        return WorkingHourEntry.builder()
                .user(user)
                .dayOfWeek(DayOfWeek.valueOf(dayRequest.getDayOfWeek().getValue()))
                .workDay(Boolean.TRUE.equals(dayRequest.getIsWorkDay()))
                .startTime(dayRequest.getStartTime() != null ? LocalTime.parse(dayRequest.getStartTime()) : null)
                .endTime(dayRequest.getEndTime() != null ? LocalTime.parse(dayRequest.getEndTime()) : null)
                .build();
    }

    private WorkingHoursResponse mapToResponse(List<WorkingHourEntry> entries) {
        List<WorkingHoursDayEntry> dayEntries = entries.stream()
                .sorted(Comparator.comparing(WorkingHourEntry::getDayOfWeek))
                .map(entity -> {
                    WorkingHoursDayEntry day = new WorkingHoursDayEntry();
                    day.setDayOfWeek(de.goaldone.backend.model.DayOfWeek.fromValue(entity.getDayOfWeek().name()));
                    day.setIsWorkDay(entity.isWorkDay());
                    day.setStartTime(entity.getStartTime() != null ? entity.getStartTime().toString() : null);
                    day.setEndTime(entity.getEndTime() != null ? entity.getEndTime().toString() : null);
                    return day;
                })
                .collect(Collectors.toList());

        WorkingHoursResponse response = new WorkingHoursResponse();
        response.setDays(dayEntries);
        return response;
    }
}
