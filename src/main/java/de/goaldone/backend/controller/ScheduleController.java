package de.goaldone.backend.controller;

import de.goaldone.backend.api.ScheduleApi;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.RecurringExceptionRequest;
import de.goaldone.backend.model.RecurringExceptionResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.security.GoaldoneUserDetails;
import de.goaldone.backend.service.ScheduleService;
import de.goaldone.backend.service.RecurringTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController extends BaseController implements ScheduleApi {

    private final ScheduleService scheduleService;
    private final RecurringTemplateService recurringTemplateService;

    @Override
    public ResponseEntity<ScheduleResponse> getSchedule(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(scheduleService.getSchedule(getCurrentUserId(), from, to));
    }

    @Override
    public ResponseEntity<ScheduleResponse> generateSchedule(GenerateScheduleRequest generateScheduleRequest) {
        return ResponseEntity.ok(scheduleService.generateSchedule(
                getCurrentUserId(),
                getCurrentOrgId(),
                generateScheduleRequest
        ));
    }

    @Override
    public ResponseEntity<ScheduleEntry> completeScheduleEntry(UUID entryId) {
        return ResponseEntity.ok(scheduleService.completeScheduleEntry(entryId, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<ScheduleEntry> pinScheduleEntry(UUID entryId) {
        return ResponseEntity.ok(scheduleService.pinScheduleEntry(entryId, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<ScheduleEntry> unpinScheduleEntry(UUID entryId) {
        return ResponseEntity.ok(scheduleService.unpinScheduleEntry(entryId, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<RecurringExceptionResponse> createRecurringException(UUID templateId, RecurringExceptionRequest recurringExceptionRequest) {
        RecurringExceptionResponse result = recurringTemplateService.createOrUpdateException(templateId, recurringExceptionRequest, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Override
    public ResponseEntity<Void> deleteRecurringException(UUID templateId, LocalDate occurrenceDate) {
        recurringTemplateService.deleteException(templateId, occurrenceDate, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.noContent().build();
    }
}
