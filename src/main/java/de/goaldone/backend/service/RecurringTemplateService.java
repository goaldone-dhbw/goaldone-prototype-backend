package de.goaldone.backend.service;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.RecurringExceptionRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurringTemplateService {

    private final RecurringTemplateRepository templateRepository;
    private final RecurringExceptionRepository exceptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public RecurringTemplatePage listTemplates(UUID userId, UUID organizationId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RecurringTemplate> templates = templateRepository
            .findByOwnerIdAndOrganizationId(userId, organizationId, pageable);

        List<RecurringTemplateResponse> content = templates.getContent().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        RecurringTemplatePage result = new RecurringTemplatePage();
        result.setContent(content);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements((int) templates.getTotalElements());
        result.setTotalPages(templates.getTotalPages());
        return result;
    }

    @Transactional(readOnly = true)
    public RecurringTemplateResponse getTemplate(UUID templateId, UUID userId, UUID organizationId) {
        RecurringTemplate template = templateRepository
            .findByIdAndOwnerIdAndOrganizationId(templateId, userId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringTemplate not found"));

        return mapToResponse(template);
    }

    @Transactional
    public RecurringTemplateResponse createTemplate(CreateRecurringTemplateRequest request, UUID userId, UUID organizationId) {
        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Extract values from JsonNullable wrappers
        String title = request.getTitle();
        String description = request.getDescription() != null && request.getDescription().isPresent()
            ? request.getDescription().get()
            : null;
        de.goaldone.backend.model.CognitiveLoad modelCogLoad = request.getCognitiveLoad();
        CognitiveLoad cogLoad = CognitiveLoad.valueOf(modelCogLoad.getValue());
        int durationMinutes = request.getDurationMinutes();

        de.goaldone.backend.model.RecurrenceType modelRecType = request.getRecurrenceRule().getType();
        RecurrenceType recType = RecurrenceType.valueOf(modelRecType.getValue());
        Integer recInterval = request.getRecurrenceRule().getInterval() != null
            ? request.getRecurrenceRule().getInterval()
            : 1;

        LocalTime prefStartTime = null;
        if (request.getPreferredStartTime() != null && request.getPreferredStartTime().isPresent()) {
            String timeStr = request.getPreferredStartTime().get();
            prefStartTime = LocalTime.parse(timeStr);
        }

        RecurringTemplate template = RecurringTemplate.builder()
            .title(title)
            .description(description)
            .cognitiveLoad(cogLoad)
            .durationMinutes(durationMinutes)
            .recurrenceType(recType)
            .recurrenceInterval(recInterval)
            .preferredStartTime(prefStartTime)
            .owner(owner)
            .organizationId(organizationId)
            .build();

        RecurringTemplate saved = templateRepository.save(template);
        return mapToResponse(saved);
    }

    @Transactional
    public RecurringTemplateResponse updateTemplate(UUID templateId, UpdateRecurringTemplateRequest request, UUID userId, UUID organizationId) {
        RecurringTemplate template = templateRepository
            .findByIdAndOwnerIdAndOrganizationId(templateId, userId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringTemplate not found"));

        String title = request.getTitle();
        String description = request.getDescription() != null && request.getDescription().isPresent()
            ? request.getDescription().get()
            : null;
        de.goaldone.backend.model.CognitiveLoad modelCogLoad = request.getCognitiveLoad();
        CognitiveLoad cogLoad = CognitiveLoad.valueOf(modelCogLoad.getValue());
        int durationMinutes = request.getDurationMinutes();

        de.goaldone.backend.model.RecurrenceType modelRecType = request.getRecurrenceRule().getType();
        RecurrenceType recType = RecurrenceType.valueOf(modelRecType.getValue());
        Integer recInterval = request.getRecurrenceRule().getInterval() != null
            ? request.getRecurrenceRule().getInterval()
            : 1;

        LocalTime prefStartTime = null;
        if (request.getPreferredStartTime() != null && request.getPreferredStartTime().isPresent()) {
            String timeStr = request.getPreferredStartTime().get();
            prefStartTime = LocalTime.parse(timeStr);
        }

        template.setTitle(title);
        template.setDescription(description);
        template.setCognitiveLoad(cogLoad);
        template.setDurationMinutes(durationMinutes);
        template.setRecurrenceType(recType);
        template.setRecurrenceInterval(recInterval);
        template.setPreferredStartTime(prefStartTime);

        RecurringTemplate updated = templateRepository.save(template);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteTemplate(UUID templateId, UUID userId, UUID organizationId) {
        RecurringTemplate template = templateRepository
            .findByIdAndOwnerIdAndOrganizationId(templateId, userId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringTemplate not found"));

        exceptionRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
    }

    @Transactional
    public RecurringExceptionResponse createOrUpdateException(
        UUID templateId,
        RecurringExceptionRequest request,
        UUID userId,
        UUID organizationId) {

        RecurringTemplate template = templateRepository
            .findByIdAndOwnerIdAndOrganizationId(templateId, userId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringTemplate not found"));

        // Validate RESCHEDULED type requires newDate and newStartTime
        if (request.getType() == RecurringExceptionType.RESCHEDULED) {
            LocalDate newDate = request.getNewDate() != null && request.getNewDate().isPresent()
                ? request.getNewDate().get()
                : null;
            String newStartTimeStr = request.getNewStartTime() != null && request.getNewStartTime().isPresent()
                ? request.getNewStartTime().get()
                : null;

            if (newDate == null) {
                throw new ValidationException("missing-new-date", "newDate is required for RESCHEDULED exceptions");
            }
            if (newStartTimeStr == null) {
                throw new ValidationException("missing-new-start-time", "newStartTime is required for RESCHEDULED exceptions");
            }
        }

        RecurringException exception = exceptionRepository
            .findByTemplateIdAndOccurrenceDate(templateId, request.getOccurrenceDate())
            .orElseGet(() -> RecurringException.builder()
                .template(template)
                .occurrenceDate(request.getOccurrenceDate())
                .build());

        exception.setType(request.getType());

        if (request.getNewDate() != null && request.getNewDate().isPresent()) {
            exception.setNewDate(request.getNewDate().get());
        } else {
            exception.setNewDate(null);
        }

        if (request.getNewStartTime() != null && request.getNewStartTime().isPresent()) {
            exception.setNewStartTime(LocalTime.parse(request.getNewStartTime().get()));
        } else {
            exception.setNewStartTime(null);
        }

        RecurringException saved = exceptionRepository.save(exception);
        return mapExceptionToResponse(saved);
    }

    @Transactional
    public void deleteException(UUID templateId, LocalDate occurrenceDate, UUID userId, UUID organizationId) {
        RecurringTemplate template = templateRepository
            .findByIdAndOwnerIdAndOrganizationId(templateId, userId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringTemplate not found"));

        RecurringException exception = exceptionRepository
            .findByTemplateIdAndOccurrenceDate(templateId, occurrenceDate)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringException not found"));

        exceptionRepository.delete(exception);
    }

    // Mapping helpers
    private RecurringTemplateResponse mapToResponse(RecurringTemplate template) {
        RecurringTemplateResponse response = new RecurringTemplateResponse();
        response.setId(template.getId());
        response.setTitle(template.getTitle());
        if (template.getDescription() != null) {
            response.setDescription(JsonNullable.of(template.getDescription()));
        }

        de.goaldone.backend.model.CognitiveLoad modelCogLoad = de.goaldone.backend.model.CognitiveLoad.fromValue(template.getCognitiveLoad().name());
        response.setCognitiveLoad(modelCogLoad);
        response.setDurationMinutes(template.getDurationMinutes());

        RecurrenceRule rule = new RecurrenceRule();
        de.goaldone.backend.model.RecurrenceType modelRecType = de.goaldone.backend.model.RecurrenceType.fromValue(template.getRecurrenceType().name());
        rule.setType(modelRecType);
        rule.setInterval(template.getRecurrenceInterval());
        response.setRecurrenceRule(rule);

        if (template.getPreferredStartTime() != null) {
            response.setPreferredStartTime(JsonNullable.of(template.getPreferredStartTime().toString()));
        }

        if (template.getCreatedAt() != null) {
            response.setCreatedAt(template.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        if (template.getUpdatedAt() != null) {
            response.setUpdatedAt(template.getUpdatedAt().atOffset(ZoneOffset.UTC));
        }

        return response;
    }

    private RecurringExceptionResponse mapExceptionToResponse(RecurringException exception) {
        RecurringExceptionResponse response = new RecurringExceptionResponse();
        response.setId(exception.getId());
        response.setTemplateId(exception.getTemplate().getId());
        response.setOccurrenceDate(exception.getOccurrenceDate());
        response.setType(exception.getType());

        if (exception.getNewDate() != null) {
            response.setNewDate(JsonNullable.of(exception.getNewDate()));
        }
        if (exception.getNewStartTime() != null) {
            response.setNewStartTime(JsonNullable.of(exception.getNewStartTime().toString()));
        }

        if (exception.getCreatedAt() != null) {
            response.setCreatedAt(exception.getCreatedAt().atOffset(ZoneOffset.UTC));
        }

        return response;
    }
}
