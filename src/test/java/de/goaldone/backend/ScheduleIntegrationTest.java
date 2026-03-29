package de.goaldone.backend;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.WorkingHourEntry;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
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

    @Autowired
    private WorkingHourEntryRepository workingHourEntryRepository;

    private void createWorkingHours(User user) {
        for (DayOfWeek dow : DayOfWeek.values()) {
            boolean isWorkDay = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            WorkingHourEntry entry = WorkingHourEntry.builder()
                    .user(user)
                    .dayOfWeek(dow)
                    .workDay(isWorkDay)
                    .startTime(isWorkDay ? LocalTime.of(8, 0) : null)
                    .endTime(isWorkDay ? LocalTime.of(17, 0) : null)
                    .build();
            workingHourEntryRepository.save(entry);
        }
    }

    private Task createTask(User user, String title) {
        return taskRepository.save(Task.builder()
                .title(title)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .estimatedDurationMinutes(60)
                .owner(user)
                .organization(user.getOrganization())
                .build());
    }

    /**
     * TEST 24: Real ScheduleEntries (source=ONE_TIME) returned correctly
     */
    @Test
    public void test24_oneTimeEntries_returned() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday
        Task task = createTask(user, "Test Task");

        ScheduleEntry entry = ScheduleEntry.builder()
                .user(user)
                .organization(user.getOrganization())
                .task(task)
                .entryDate(now)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .entryType(ScheduleEntryType.TASK)
                .generatedAt(Instant.now())
                .build();
        scheduleEntryRepository.save(entry);

        mockMvc.perform(get("/schedule?from=" + now + "&to=" + now)
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
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday

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

        mockMvc.perform(get("/schedule?from=" + now + "&to=" + now)
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
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday
        Task task = createTask(user, "Afternoon Task");

        // Add ONE_TIME entry at 10:00
        ScheduleEntry oneTime = ScheduleEntry.builder()
                .user(user)
                .organization(user.getOrganization())
                .task(task)
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

        MvcResult result = mockMvc.perform(get("/schedule?from=" + now + "&to=" + now)
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
     * TEST 27: SKIPPED exception -> entry omitted from response
     */
    @Test
    public void test27_skippedException_omittedFromResponse() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday
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

        mockMvc.perform(get("/schedule?from=" + now + "&to=" + tomorrow)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    /**
     * TEST 28: RESCHEDULED exception -> moved to new date
     */
    @Test
    public void test28_rescheduledException_movedToNewDate() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday

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

        mockMvc.perform(get("/schedule?from=" + now + "&to=" + now.plusDays(7))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray());
    }

    /**
     * TEST 29: COMPLETED exception -> isCompleted=true on virtual entry
     */
    @Test
    public void test29_completedException_markedCompleted() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        LocalDate now = LocalDate.of(2026, 3, 30); // A Monday

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

        mockMvc.perform(get("/schedule?from=" + now + "&to=" + now)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].source").value("RECURRING"));
    }
}
