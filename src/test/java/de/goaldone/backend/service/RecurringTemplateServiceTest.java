package de.goaldone.backend.service;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.RecurringExceptionRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTemplateServiceTest {

    @Mock
    private RecurringTemplateRepository templateRepository;

    @Mock
    private RecurringExceptionRepository exceptionRepository;

    @Mock
    private UserRepository userRepository;

    private RecurringTemplateService service;
    private UUID userId;
    private UUID orgId;
    private User testUser;

    @BeforeEach
    void setup() {
        service = new RecurringTemplateService(
            templateRepository,
            exceptionRepository,
            userRepository
        );

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        testUser = User.builder().id(userId).build();
    }

    // TEST 1 - createTemplate: all required fields set → entity correctly persisted
    @Test
    void createTemplate_withAllFields_shouldPersist() {
        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Daily Standup");
        request.setDescription(JsonNullable.of("Team standup"));
        request.setCognitiveLoad(de.goaldone.backend.model.CognitiveLoad.LOW);
        request.setDurationMinutes(30);

        RecurrenceRule ruleReq = new RecurrenceRule();
        ruleReq.setType(RecurrenceType.DAILY);
        ruleReq.setInterval(1);
        request.setRecurrenceRule(ruleReq);
        request.setPreferredStartTime(JsonNullable.of("09:00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            RecurringTemplate template = inv.getArgument(0);
            template.setId(UUID.randomUUID());
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            return template;
        });

        RecurringTemplateResponse response = service.createTemplate(request, userId, orgId);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Daily Standup");
        assertThat(response.getCognitiveLoad().getValue()).isEqualTo("LOW");
        assertThat(response.getDurationMinutes()).isEqualTo(30);

        verify(templateRepository).save(any(RecurringTemplate.class));
    }

    // TEST 2 - createTemplate: organizationId from JWT, not from request
    @Test
    void createTemplate_shouldSetOrganizationIdFromJwt() {
        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Test");
        request.setCognitiveLoad(de.goaldone.backend.model.CognitiveLoad.MEDIUM);
        request.setDurationMinutes(60);

        RecurrenceRule ruleReq = new RecurrenceRule();
        ruleReq.setType(RecurrenceType.WEEKLY);
        ruleReq.setInterval(1);
        request.setRecurrenceRule(ruleReq);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            RecurringTemplate template = inv.getArgument(0);
            template.setId(UUID.randomUUID());
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            return template;
        });

        service.createTemplate(request, userId, orgId);

        verify(templateRepository).save(argThat(template ->
            template.getOrganizationId().equals(orgId)
        ));
    }

    // TEST 3 - updateTemplate: all fields replaced (PUT semantics)
    @Test
    void updateTemplate_shouldReplaceAllFields() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate existing = RecurringTemplate.builder()
            .id(templateId)
            .title("Old Title")
            .description("Old Desc")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        UpdateRecurringTemplateRequest request = new UpdateRecurringTemplateRequest();
        request.setTitle("New Title");
        request.setDescription(JsonNullable.of("New Desc"));
        request.setCognitiveLoad(de.goaldone.backend.model.CognitiveLoad.HIGH);
        request.setDurationMinutes(90);

        RecurrenceRule ruleReq = new RecurrenceRule();
        ruleReq.setType(RecurrenceType.DAILY);
        ruleReq.setInterval(1);
        request.setRecurrenceRule(ruleReq);

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(existing));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            RecurringTemplate template = inv.getArgument(0);
            template.setUpdatedAt(LocalDateTime.now());
            return template;
        });

        RecurringTemplateResponse response = service.updateTemplate(templateId, request, userId, orgId);

        assertThat(response.getTitle()).isEqualTo("New Title");
        if (response.getDescription() != null && response.getDescription().isPresent()) {
            assertThat(response.getDescription().get()).isEqualTo("New Desc");
        }
        assertThat(response.getCognitiveLoad().getValue()).isEqualTo("HIGH");
        assertThat(response.getDurationMinutes()).isEqualTo(90);
    }

    // TEST 4 - updateTemplate: existing RecurringExceptions remain untouched
    @Test
    void updateTemplate_shouldNotAffectExceptions() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate existing = RecurringTemplate.builder()
            .id(templateId)
            .title("Old")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        UpdateRecurringTemplateRequest request = new UpdateRecurringTemplateRequest();
        request.setTitle("Updated");
        request.setCognitiveLoad(de.goaldone.backend.model.CognitiveLoad.MEDIUM);
        request.setDurationMinutes(45);

        RecurrenceRule ruleReq = new RecurrenceRule();
        ruleReq.setType(RecurrenceType.DAILY);
        ruleReq.setInterval(1);
        request.setRecurrenceRule(ruleReq);

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(existing));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateTemplate(templateId, request, userId, orgId);

        verify(exceptionRepository, never()).deleteByTemplateId(any());
    }

    // TEST 5 - deleteTemplate: cascades exceptions
    @Test
    void deleteTemplate_shouldCascadeExceptions() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
            .id(templateId)
            .title("Test")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(template));

        service.deleteTemplate(templateId, userId, orgId);

        verify(exceptionRepository).deleteByTemplateId(templateId);
        verify(templateRepository).delete(template);
    }

    // TEST 6 - createOrUpdateException: upsert semantics
    @Test
    void createOrUpdateException_shouldUpsert() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
            .id(templateId)
            .title("Test")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        LocalDate occurrenceDate = LocalDate.of(2026, 3, 27);

        RecurringException existing = RecurringException.builder()
            .id(UUID.randomUUID())
            .template(template)
            .occurrenceDate(occurrenceDate)
            .type(RecurringExceptionType.COMPLETED)
            .build();

        RecurringExceptionRequest request = new RecurringExceptionRequest();
        request.setOccurrenceDate(occurrenceDate);
        request.setType(RecurringExceptionType.SKIPPED);

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(template));
        when(exceptionRepository.findByTemplateIdAndOccurrenceDate(templateId, occurrenceDate))
            .thenReturn(Optional.of(existing));
        when(exceptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecurringExceptionResponse response = service.createOrUpdateException(templateId, request, userId, orgId);

        assertThat(response.getType()).isEqualTo(RecurringExceptionType.SKIPPED);
        verify(exceptionRepository).save(argThat(exc ->
            exc.getType() == RecurringExceptionType.SKIPPED
        ));
    }

    // TEST 7 - createOrUpdateException: RESCHEDULED without newDate → 400
    @Test
    void createOrUpdateException_rescheduleWithoutNewDate_shouldThrow() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
            .id(templateId)
            .title("Test")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        RecurringExceptionRequest request = new RecurringExceptionRequest();
        request.setOccurrenceDate(LocalDate.now());
        request.setType(RecurringExceptionType.RESCHEDULED);
        request.setNewStartTime(JsonNullable.of("10:00"));
        // newDate is null

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.createOrUpdateException(templateId, request, userId, orgId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("newDate is required for RESCHEDULED exceptions");
    }

    // TEST 8 - createOrUpdateException: RESCHEDULED without newStartTime → 400
    @Test
    void createOrUpdateException_rescheduleWithoutNewStartTime_shouldThrow() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
            .id(templateId)
            .title("Test")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        RecurringExceptionRequest request = new RecurringExceptionRequest();
        request.setOccurrenceDate(LocalDate.now());
        request.setType(RecurringExceptionType.RESCHEDULED);
        request.setNewDate(JsonNullable.of(LocalDate.now().plusDays(1)));
        // newStartTime is null

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.createOrUpdateException(templateId, request, userId, orgId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("newStartTime is required for RESCHEDULED exceptions");
    }

    // TEST 9 - deleteException: not found → 404
    @Test
    void deleteException_notFound_shouldThrow() {
        UUID templateId = UUID.randomUUID();
        LocalDate occurrenceDate = LocalDate.now();
        RecurringTemplate template = RecurringTemplate.builder()
            .id(templateId)
            .title("Test")
            .cognitiveLoad(CognitiveLoad.LOW)
            .durationMinutes(30)
            .build();

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.of(template));
        when(exceptionRepository.findByTemplateIdAndOccurrenceDate(templateId, occurrenceDate))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteException(templateId, occurrenceDate, userId, orgId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("RecurringException not found");
    }

    // TEST 10 - Tenant isolation: getTemplate with foreign organizationId → 404
    @Test
    void getTemplate_foreignOrganizationId_shouldThrow() {
        UUID templateId = UUID.randomUUID();
        UUID foreignOrgId = UUID.randomUUID();

        when(templateRepository.findByIdAndOwnerIdAndOrganizationId(templateId, userId, orgId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(templateId, userId, orgId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
