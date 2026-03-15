package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.model.*;
import de.goaldone.backend.security.GoaldoneUserDetails;
import de.goaldone.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TaskService taskService;

    @Override
    public ResponseEntity<TaskPage> listTasks(Integer page, Integer size, TaskStatus status, LocalDate from, LocalDate to) {
        return ResponseEntity.ok(taskService.listTasks(
                getCurrentUserId(),
                getCurrentOrgId(),
                status,
                from,
                to,
                PageRequest.of(page, size)
        ));
    }

    @Override
    public ResponseEntity<TaskResponse> createTask(CreateTaskRequest createTaskRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(createTaskRequest, getCurrentUserId(), getCurrentOrgId()));
    }

    @Override
    public ResponseEntity<TaskResponse> getTask(UUID taskId) {
        return ResponseEntity.ok(taskService.getTask(taskId, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<TaskResponse> updateTask(UUID taskId, UpdateTaskRequest updateTaskRequest) {
        return ResponseEntity.ok(taskService.updateTask(taskId, updateTaskRequest, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<Void> deleteTask(UUID taskId) {
        taskService.deleteTask(taskId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TaskResponse> completeTask(UUID taskId) {
        return ResponseEntity.ok(taskService.completeTask(taskId, getCurrentUserId()));
    }

    @Override
    public ResponseEntity<TaskResponse> reopenTask(UUID taskId) {
        return ResponseEntity.ok(taskService.reopenTask(taskId, getCurrentUserId()));
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
                throw new RuntimeException("Current user has no organization");
            }
            return orgId;
        }
        throw new RuntimeException("GoaldoneUserDetails not found in security context");
    }
}

