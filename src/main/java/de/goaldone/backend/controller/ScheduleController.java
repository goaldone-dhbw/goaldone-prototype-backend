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
public class ScheduleController extends BaseController implements ScheduleApi {

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
}
