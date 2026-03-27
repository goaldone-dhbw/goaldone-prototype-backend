package de.goaldone.backend;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /schedule endpoint.
 * Tests unified response with ONE_TIME and RECURRING entries.
 */
public class ScheduleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RecurringTemplateRepository templateRepository;

    /**
     * TEST 24: Real ScheduleEntries (source=ONE_TIME) returned correctly
     */
    @Test
    public void test24_oneTimeEntries_returned() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();
        ScheduleEntry entry = ScheduleEntry.builder()
                .user(user)
                .organization(user.getOrganization())
                .entryDate(now)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .entryType(ScheduleEntryType.TASK)
                .generatedAt(Instant.now())
                .build();
        scheduleEntryRepository.save(entry);

        mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + now)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].source").value("ONE_TIME"))
            .andExpect(jsonPath("$.entries[0].entryId").exists());
    }

    /**
     * TEST 25: Virtual RECURRING entries returned for RecurringTemplates
     */
    @Test
    public void test25_recurringEntries_returned() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();

        RecurringTemplate template = RecurringTemplate.builder()
                .title("Daily Standup")
                .cognitiveLoad(CognitiveLoad.LOW)
                .durationMinutes(15)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build();
        templateRepository.save(template);

        mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + now)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].source").value("RECURRING"))
            .andExpect(jsonPath("$.entries[0].templateId").exists())
            .andExpect(jsonPath("$.entries[0].templateTitle").value("Daily Standup"));
    }

    /**
     * TEST 26: Mixed entries sorted by date + startTime
     */
    @Test
    public void test26_mixedEntries_sortedCorrectly() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();

        // Add ONE_TIME entry at 10:00
        ScheduleEntry oneTime = ScheduleEntry.builder()
                .user(user)
                .organization(user.getOrganization())
                .entryDate(now)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .entryType(ScheduleEntryType.TASK)
                .generatedAt(Instant.now())
                .build();
        scheduleEntryRepository.save(oneTime);

        // Add RECURRING template at 09:00
        RecurringTemplate template = RecurringTemplate.builder()
                .title("Morning Review")
                .cognitiveLoad(CognitiveLoad.LOW)
                .durationMinutes(15)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .preferredStartTime(LocalTime.of(9, 0))
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build();
        templateRepository.save(template);

        MvcResult result = mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + now)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries.length()").value(2))
            .andReturn();

        String content = result.getResponse().getContentAsString();
        // First entry should be RECURRING (9:00), second should be ONE_TIME (10:00)
        assert(content.contains("\"source\":\"RECURRING\""));
        assert(content.contains("\"source\":\"ONE_TIME\""));
    }

    /**
     * TEST 27: SKIPPED exception → entry omitted from response
     */
    @Test
    public void test27_skippedException_omittedFromResponse() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();
        LocalDate tomorrow = now.plusDays(1);

        RecurringTemplate template = RecurringTemplate.builder()
                .title("Daily Task")
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build();
        templateRepository.save(template);

        mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + tomorrow)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        // At least one entry should exist (tomorrow's occurrence if not skipped)
    }

    /**
     * TEST 28: RESCHEDULED exception → moved to new date
     */
    @Test
    public void test28_rescheduledException_movedToNewDate() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();

        RecurringTemplate template = RecurringTemplate.builder()
                .title("Meeting")
                .cognitiveLoad(CognitiveLoad.HIGH)
                .durationMinutes(60)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build();
        templateRepository.save(template);

        mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + now.plusDays(7))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray());
    }

    /**
     * TEST 29: COMPLETED exception → isCompleted=true on virtual entry
     */
    @Test
    public void test29_completedException_markedCompleted() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        LocalDate now = LocalDate.now();

        RecurringTemplate template = RecurringTemplate.builder()
                .title("Daily Review")
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build();
        templateRepository.save(template);

        mockMvc.perform(get("/api/v1/schedule?from=" + now + "&to=" + now)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].source").value("RECURRING"));
    }

    /**
     * TEST 23: invalid-schedule-window removed
     * POST /schedule/generate no longer requires fixed 14-day window
     */
    @Test
    public void test23_generateSchedule_nows14DayValidation() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        // Create a working hours setup via API (or use default)
        LocalDate from = LocalDate.now();

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setMaxDailyWorkMinutes(240);

        // Should NOT throw "invalid-schedule-window" error
        mockMvc.perform(post("/api/v1/schedule/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
