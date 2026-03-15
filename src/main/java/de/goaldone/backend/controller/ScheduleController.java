package de.goaldone.backend.controller;

import de.goaldone.backend.api.ScheduleApi;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.security.GoaldoneUserDetails;
import de.goaldone.backend.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController implements ScheduleApi {

    private final ScheduleService scheduleService;

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

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        throw new RuntimeException("Current user not found in security context");
    }

    private UUID getCurrentOrgId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (details instanceof GoaldoneUserDetails) {
            UUID orgId = ((GoaldoneUserDetails) details).getOrganizationId();
            if (orgId == null) {
                // SUPER_ADMIN might have null orgId, but tasks/schedules need an orgId in this context
                // Requirement says "extract orgId from JWT, never from request".
                throw new RuntimeException("Current user has no organization");
            }
            return orgId;
        }
        throw new RuntimeException("GoaldoneUserDetails not found in security context");
    }
}
