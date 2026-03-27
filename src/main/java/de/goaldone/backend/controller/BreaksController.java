package de.goaldone.backend.controller;

import de.goaldone.backend.api.BreaksApi;
import de.goaldone.backend.model.BreakResponse;
import de.goaldone.backend.model.CreateBreakRequest;
import de.goaldone.backend.service.BreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BreaksController extends BaseController implements BreaksApi {

    private final BreakService breakService;

    @Override
    public ResponseEntity<List<BreakResponse>> listBreaks() {
        return ResponseEntity.ok(breakService.listBreaks(getCurrentUserId()));
    }

    @Override
    public ResponseEntity<BreakResponse> createBreak(CreateBreakRequest createBreakRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(breakService.createBreak(createBreakRequest, getCurrentUserId(), getCurrentOrgId()));
    }

    @Override
    public ResponseEntity<BreakResponse> updateBreak(UUID breakId, CreateBreakRequest createBreakRequest) {
        return ResponseEntity.ok(breakService.updateBreak(breakId, createBreakRequest, getCurrentUserId(), getCurrentOrgId()));
    }

    @Override
    public ResponseEntity<Void> deleteBreak(UUID breakId) {
        breakService.deleteBreak(breakId, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.noContent().build();
    }
}

