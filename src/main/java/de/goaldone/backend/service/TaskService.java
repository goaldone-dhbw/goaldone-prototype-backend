package de.goaldone.backend.service;

import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public TaskPage listTasks(UUID ownerId, UUID orgId, de.goaldone.backend.model.TaskStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        // Convert model status to entity status
        de.goaldone.backend.entity.enums.TaskStatus entityStatus = status != null 
                ? de.goaldone.backend.entity.enums.TaskStatus.valueOf(status.getValue()) 
                : null;

        Page<Task> tasks = taskRepository.findAllByFilters(ownerId, orgId, entityStatus, from, to, pageable);

        return TaskPage.builder()
                .page(tasks.getNumber())
                .size(tasks.getSize())
                .totalElements((int) tasks.getTotalElements())
                .totalPages(tasks.getTotalPages())
                .content(tasks.getContent().stream().map(this::mapToTaskResponse).toList())
                .build();
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, UUID ownerId, UUID orgId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Organization customOrg = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Verify user belongs to org? Usually handled by context, but strictly speaking owner.getOrganization().getId() should be checked against orgId.
        if (!owner.getOrganization().getId().equals(orgId)) {
             throw new RuntimeException("User organization mismatch");
        }

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription().orElse(null))
                .status(de.goaldone.backend.entity.enums.TaskStatus.OPEN)
                .cognitiveLoad(de.goaldone.backend.entity.enums.CognitiveLoad.valueOf(request.getCognitiveLoad().getValue()))
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .deadline(request.getDeadline().orElse(null))
                .owner(owner)
                .organization(customOrg)
                .build();


        if (request.getRecurrence() != null) {
            task.setRecurrenceType(de.goaldone.backend.entity.enums.RecurrenceType.valueOf(request.getRecurrence().getType().getValue()));
            task.setRecurrenceInterval(request.getRecurrence().getInterval());
        }

        task = taskRepository.save(task);

        return mapToTaskResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UUID ownerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Access denied: You do not own this task");
        }

        return mapToTaskResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest request, UUID ownerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Access denied: You do not own this task");
        }

        if (request.getTitle() != null) {
             task.setTitle(request.getTitle());
        }
        
        if (request.getDescription().isPresent()) {
            task.setDescription(request.getDescription().get());
        }

        if (request.getCognitiveLoad() != null) {
            task.setCognitiveLoad(de.goaldone.backend.entity.enums.CognitiveLoad.valueOf(request.getCognitiveLoad().getValue()));
        }

        if (request.getEstimatedDurationMinutes() != null) {
            task.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }

        if (request.getDeadline().isPresent()) {
            task.setDeadline(request.getDeadline().get());
        }

        if (request.getRecurrence() != null) {
             RecurrenceRule rule = request.getRecurrence();
             task.setRecurrenceType(de.goaldone.backend.entity.enums.RecurrenceType.valueOf(rule.getType().getValue()));
             task.setRecurrenceInterval(rule.getInterval());
        }

        task = taskRepository.save(task);
        return mapToTaskResponse(task);
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID ownerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Access denied: You do not own this task");
        }

        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId, UUID ownerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Access denied: You do not own this task");
        }
        
        if (task.getStatus() == de.goaldone.backend.entity.enums.TaskStatus.DONE) {
             // Potentially 409, but returning current state is also fine unless strictly required.
             // Spec says 409 "Task is already done".
             throw new RuntimeException("Task is already done"); // Map to 409 in handler later or let it 500
        }

        task.setStatus(de.goaldone.backend.entity.enums.TaskStatus.DONE);
        task.setCompletedAt(Instant.now());
        
        task = taskRepository.save(task);
        return mapToTaskResponse(task);
    }

    @Transactional
    public TaskResponse reopenTask(UUID taskId, UUID ownerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Access denied: You do not own this task");
        }
        
        if (task.getStatus() == de.goaldone.backend.entity.enums.TaskStatus.OPEN) {
             // Spec says 409 "Task is already open"
             throw new RuntimeException("Task is already open");
        }

        task.setStatus(de.goaldone.backend.entity.enums.TaskStatus.OPEN);
        task.setCompletedAt(null);

        task = taskRepository.save(task);
        return mapToTaskResponse(task);
    }

    private TaskResponse mapToTaskResponse(Task task) {
        RecurrenceRule recurrenceRule = null;
        if (task.getRecurrenceType() != null) {
            recurrenceRule = RecurrenceRule.builder()
                    .type(RecurrenceType.fromValue(task.getRecurrenceType().name()))
                    .interval(task.getRecurrenceInterval())
                    .build();
        }

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(JsonNullable.of(task.getDescription()))
                .status(TaskStatus.fromValue(task.getStatus().name()))
                .cognitiveLoad(CognitiveLoad.fromValue(task.getCognitiveLoad().name()))
                .estimatedDurationMinutes(task.getEstimatedDurationMinutes())
                .deadline(JsonNullable.of(task.getDeadline()))
                .recurrence(recurrenceRule)
                .ownerId(task.getOwner().getId())
                .organizationId(task.getOrganization().getId())
                .parentTaskId(task.getParentTask() != null ? JsonNullable.of(task.getParentTask().getId()) : JsonNullable.undefined())
                .createdAt(OffsetDateTime.ofInstant(task.getCreatedAt(), ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(task.getUpdatedAt(), ZoneOffset.UTC))
                .completedAt(task.getCompletedAt() != null ? JsonNullable.of(OffsetDateTime.ofInstant(task.getCompletedAt(), ZoneOffset.UTC)) : JsonNullable.undefined())
                .build();
    }
}

