package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.RecurringExceptionType;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.WorkingHoursDayEntry;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.RecurringExceptionRepository;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduleService.generateSchedule() — the MSTF planning algorithm.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceGenerateTest {

    @Mock private ScheduleEntryRepository scheduleEntryRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private BreakRepository breakRepository;
    @Mock private RecurringTemplateRepository recurringTemplateRepository;
    @Mock private RecurringExceptionRepository recurringExceptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkingHoursService workingHoursService;

    @InjectMocks private ScheduleService scheduleService;

    private UUID userId;
    private UUID orgId;
    private User testUser;
    private Organization testOrg;

    // Monday 2026-03-30
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 30);

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        testOrg = new Organization();
        testOrg.setId(orgId);
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .organization(testOrg)
                .build();
    }

    // ════════════════════════════════��══════════════════
    // Helpers
    // ═══════════════════════════════════════════════════

    private WorkingHoursResponse buildWorkingHoursResponse() {
        List<WorkingHoursDayEntry> days = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            WorkingHoursDayEntry day = new WorkingHoursDayEntry();
            day.setDayOfWeek(de.goaldone.backend.model.DayOfWeek.fromValue(dow.name()));
            boolean isWorkDay = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            day.setIsWorkDay(isWorkDay);
            day.setStartTime(isWorkDay ? "08:00" : null);
            day.setEndTime(isWorkDay ? "17:00" : null);
            days.add(day);
        }
        WorkingHoursResponse resp = new WorkingHoursResponse();
        resp.setDays(days);
        return resp;
    }

    private void stubCommonRepos(LocalDate from) {
        when(workingHoursService.getWorkingHours(userId)).thenReturn(buildWorkingHoursResponse());
        lenient().when(scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsPinnedTrue(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(breakRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        lenient().when(recurringTemplateRepository.findByOwnerIdAndOrganizationId(userId, orgId))
                .thenReturn(Collections.emptyList());
        lenient().when(scheduleEntryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Task buildTask(String title, int durationMinutes, CognitiveLoad load, LocalDate deadline, LocalDate startDate) {
        return Task.builder()
                .id(UUID.randomUUID())
                .title(title)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(load)
                .estimatedDurationMinutes(durationMinutes)
                .deadline(deadline)
                .startDate(startDate)
                .owner(testUser)
                .organization(testOrg)
                .build();
    }

    private Task buildTask(String title, int durationMinutes, CognitiveLoad load) {
        return buildTask(title, durationMinutes, load, null, null);
    }

    private GenerateScheduleRequest request(LocalDate from) {
        return request(from, null);
    }

    private GenerateScheduleRequest request(LocalDate from, Integer maxDaily) {
        GenerateScheduleRequest req = new GenerateScheduleRequest();
        req.setFrom(from);
        req.setMaxDailyWorkMinutes(maxDaily);
        return req;
    }

    // ═══════════════════════════════════════════════════
    // Phase 1: Pre-Flight
    // ═══════════════════════════════════════════════════

    @Test
    void generateSchedule_noWorkingHours_throws400() {
        when(workingHoursService.getWorkingHours(userId)).thenThrow(new ResourceNotFoundException("not found"));

        assertThatThrownBy(() -> scheduleService.generateSchedule(userId, orgId, request(MONDAY)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("working-hours-missing");
                });
    }

    @Test
    void generateSchedule_emptyWorkingHoursDays_throws400() {
        WorkingHoursResponse resp = new WorkingHoursResponse();
        resp.setDays(Collections.emptyList());
        when(workingHoursService.getWorkingHours(userId)).thenReturn(resp);

        assertThatThrownBy(() -> scheduleService.generateSchedule(userId, orgId, request(MONDAY)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                });
    }

    @Test
    void generateSchedule_nullWorkingHoursResponse_throws400() {
        when(workingHoursService.getWorkingHours(userId)).thenReturn(null);

        assertThatThrownBy(() -> scheduleService.generateSchedule(userId, orgId, request(MONDAY)))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ═══════════════════════════════════════════════════
    // Phase 2: Tabula Rasa & Blocker
    // ═══════════════════════════════════════════════════

    @Test
    void generateSchedule_deletesOldEntriesBeforeBlockerCalc() {
        stubCommonRepos(MONDAY);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(Collections.emptyList());

        scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Verify delete is called
        verify(scheduleEntryRepository).deleteByUserIdAndEntryDateGreaterThanEqualAndIsCompletedFalseAndIsPinnedFalse(userId, MONDAY);
        verify(scheduleEntryRepository).flush();

        // Verify blocker queries happen after delete (completed/pinned entries)
        var inOrder = inOrder(scheduleEntryRepository);
        inOrder.verify(scheduleEntryRepository).deleteByUserIdAndEntryDateGreaterThanEqualAndIsCompletedFalseAndIsPinnedFalse(userId, MONDAY);
        inOrder.verify(scheduleEntryRepository).flush();
        inOrder.verify(scheduleEntryRepository).findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(userId, MONDAY);
    }

    @Test
    void generateSchedule_completedEntriesReduceBudget() {
        stubCommonRepos(MONDAY);

        // 60-min completed entry on Monday
        ScheduleEntry completed = ScheduleEntry.builder()
                .entryDate(MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .entryType(ScheduleEntryType.TASK)
                .build();
        when(scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(userId, MONDAY))
                .thenReturn(List.of(completed));

        // One task that takes exactly 240min (default maxDaily)
        Task task = buildTask("Big Task", 240, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Budget on Monday = 240 - 60 = 180min, so task should be split across 2 days
        assertThat(resp.getEntries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void generateSchedule_pinnedEntriesReduceBudget() {
        stubCommonRepos(MONDAY);

        ScheduleEntry pinned = ScheduleEntry.builder()
                .entryDate(MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0)) // 240 min - fills whole budget
                .entryType(ScheduleEntryType.TASK)
                .build();
        when(scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsPinnedTrue(userId, MONDAY))
                .thenReturn(List.of(pinned));

        Task task = buildTask("Small Task", 30, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Monday fully blocked (240min pinned), task should go to Tuesday
        List<de.goaldone.backend.model.ScheduleEntry> entries = resp.getEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getDate()).isEqualTo(MONDAY.plusDays(1)); // Tuesday
    }

    @Test
    void generateSchedule_breaksReduceBudget() {
        stubCommonRepos(MONDAY);

        Break lunchBreak = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(MONDAY)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .build();
        when(breakRepository.findByUserId(userId)).thenReturn(List.of(lunchBreak));

        Task task = buildTask("Task", 240, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // 240 budget - 60 break = 180 available on Monday, so split across days
        assertThat(resp.getEntries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void generateSchedule_recurringTemplatesReduceBudget() {
        stubCommonRepos(MONDAY);

        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(templateId)
                .title("Daily Standup")
                .durationMinutes(60)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .createdAt(MONDAY.minusDays(10).atStartOfDay())
                .build();
        when(recurringTemplateRepository.findByOwnerIdAndOrganizationId(userId, orgId))
                .thenReturn(List.of(template));

        Task task = buildTask("Task", 240, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Budget per day = 240 - 60 = 180, so 240min task takes 2 days
        assertThat(resp.getEntries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void generateSchedule_skippedTemplateExceptionDoesNotReduceBudget() {
        stubCommonRepos(MONDAY);

        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(templateId)
                .title("Daily Standup")
                .durationMinutes(120)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .createdAt(MONDAY.minusDays(10).atStartOfDay())
                .build();
        when(recurringTemplateRepository.findByOwnerIdAndOrganizationId(userId, orgId))
                .thenReturn(List.of(template));

        // SKIPPED exception on Monday
        RecurringException skipped = RecurringException.builder()
                .template(template)
                .occurrenceDate(MONDAY)
                .type(RecurringExceptionType.SKIPPED)
                .build();
        when(recurringExceptionRepository.findByTemplateIdInAndOccurrenceDateBetween(eq(List.of(templateId)), any(), any()))
                .thenReturn(List.of(skipped));

        // Task that exactly fills 240 min
        Task task = buildTask("Task", 240, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Monday has full 240 budget (skipped template doesn't block), task fits in 1 day
        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY);
    }

    // ═══════════════════════════════════════════════════
    // Phase 3: Task Pool & Phase 4: Scheduling
    // ═══════════════════════════════════════════════════

    @Test
    void generateSchedule_emptyPool_returnsEmptyEntries() {
        stubCommonRepos(MONDAY);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(Collections.emptyList());

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).isEmpty();
        assertThat(resp.getFrom()).isEqualTo(MONDAY);
        assertThat(resp.getTo()).isEqualTo(MONDAY);
    }

    @Test
    void generateSchedule_singleTaskFitsInOneDay() {
        stubCommonRepos(MONDAY);
        Task task = buildTask("Quick Task", 60, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY);
        assertThat(resp.getTo()).isEqualTo(MONDAY);
    }

    @Test
    void generateSchedule_taskSplitAcrossDays() {
        stubCommonRepos(MONDAY);
        // Task takes 300min > 240min maxDaily default
        Task task = buildTask("Big Task", 300, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Split: 240 on Monday, 60 on Tuesday
        assertThat(resp.getEntries()).hasSize(2);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY);
        assertThat(resp.getEntries().get(1).getDate()).isEqualTo(MONDAY.plusDays(1)); // Tuesday

        // Verify total duration = 300
        int totalMinutes = resp.getEntries().stream()
                .mapToInt(e -> (int) ChronoUnit.MINUTES.between(
                        LocalTime.parse(e.getStartTime()), LocalTime.parse(e.getEndTime())))
                .sum();
        assertThat(totalMinutes).isEqualTo(300);
    }

    @Test
    void generateSchedule_customMaxDailyWorkMinutes() {
        stubCommonRepos(MONDAY);
        Task task = buildTask("Task", 200, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        // Only 100 minutes per day
        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY, 100));

        // Should be split into 2 days: 100 + 100
        assertThat(resp.getEntries()).hasSize(2);
    }

    @Test
    void generateSchedule_skipsWeekends() {
        stubCommonRepos(MONDAY);

        // Friday 2026-04-03
        LocalDate friday = LocalDate.of(2026, 4, 3);
        // Task takes 480min (2 full days)
        Task task = buildTask("Weekend Crosser", 480, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(friday));

        // Friday (240), skip Sat/Sun, Monday (240)
        assertThat(resp.getEntries()).hasSize(2);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(friday);           // Friday
        assertThat(resp.getEntries().get(1).getDate()).isEqualTo(friday.plusDays(3)); // Monday
    }

    @Test
    void generateSchedule_mstfSortingSlackAscending() {
        stubCommonRepos(MONDAY);

        // Task with tight deadline (low slack) should come first
        Task urgent = buildTask("Urgent", 60, CognitiveLoad.MEDIUM, MONDAY.plusDays(1), null);
        Task relaxed = buildTask("Relaxed", 60, CognitiveLoad.MEDIUM, MONDAY.plusDays(10), null);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(relaxed, urgent)); // reversed order in pool

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(2);
        // Both on Monday, urgent first
        assertThat(resp.getEntries().get(0).getTaskTitle().get()).isEqualTo("Urgent");
        assertThat(resp.getEntries().get(1).getTaskTitle().get()).isEqualTo("Relaxed");
    }

    @Test
    void generateSchedule_mstfSortingCognitiveLoadHighFirst() {
        stubCommonRepos(MONDAY);

        // Same slack (no deadline = MAX_VALUE), but different cognitive load
        Task low = buildTask("Low Load", 60, CognitiveLoad.LOW);
        Task high = buildTask("High Load", 60, CognitiveLoad.HIGH);
        Task medium = buildTask("Medium Load", 60, CognitiveLoad.MEDIUM);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(low, medium, high)); // reversed order in pool

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(3);
        // All on Monday, HIGH first, then MEDIUM, then LOW
        assertThat(resp.getEntries().get(0).getTaskTitle().get()).isEqualTo("High Load");
        assertThat(resp.getEntries().get(1).getTaskTitle().get()).isEqualTo("Medium Load");
        assertThat(resp.getEntries().get(2).getTaskTitle().get()).isEqualTo("Low Load");
    }

    @Test
    void generateSchedule_tasksWithoutDeadlineHaveInfiniteSlack() {
        stubCommonRepos(MONDAY);

        // Task with deadline gets scheduled first (lower slack)
        Task withDeadline = buildTask("Deadline Task", 60, CognitiveLoad.LOW, MONDAY.plusDays(5), null);
        Task noDeadline = buildTask("No Deadline", 60, CognitiveLoad.HIGH); // HIGH but infinite slack

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(noDeadline, withDeadline));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        // Deadline task first despite LOW cognitive load (lower slack wins)
        assertThat(resp.getEntries().get(0).getTaskTitle().get()).isEqualTo("Deadline Task");
        assertThat(resp.getEntries().get(1).getTaskTitle().get()).isEqualTo("No Deadline");
    }

    @Test
    void generateSchedule_deadlineMissedWarning() {
        stubCommonRepos(MONDAY);

        // Task with deadline in the past
        Task missed = buildTask("Overdue", 60, CognitiveLoad.LOW, MONDAY.minusDays(1), null);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(missed));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getWarnings()).hasSize(1);
        assertThat(resp.getWarnings().get(0)).startsWith("deadline-missed:");
        assertThat(resp.getEntries()).isEmpty();
    }

    @Test
    void generateSchedule_startDateFilter() {
        stubCommonRepos(MONDAY);

        // Task not ready until Wednesday
        LocalDate wednesday = MONDAY.plusDays(2);
        Task future = buildTask("Future Task", 60, CognitiveLoad.MEDIUM, null, wednesday);
        Task now = buildTask("Ready Now", 60, CognitiveLoad.MEDIUM);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(future, now));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(2);
        // "Ready Now" on Monday, "Future Task" on Wednesday
        assertThat(resp.getEntries().get(0).getTaskTitle().get()).isEqualTo("Ready Now");
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY);
        assertThat(resp.getEntries().get(1).getTaskTitle().get()).isEqualTo("Future Task");
        assertThat(resp.getEntries().get(1).getDate()).isEqualTo(wednesday);
    }

    @Test
    void generateSchedule_multipleTasks_correctTimeSlots() {
        stubCommonRepos(MONDAY);

        Task first = buildTask("First", 60, CognitiveLoad.HIGH);
        Task second = buildTask("Second", 90, CognitiveLoad.MEDIUM);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(first, second));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(2);
        // First task: 08:00 - 09:00
        assertThat(resp.getEntries().get(0).getStartTime()).isEqualTo("08:00");
        assertThat(resp.getEntries().get(0).getEndTime()).isEqualTo("09:00");
        // Second task: 09:00 - 10:30
        assertThat(resp.getEntries().get(1).getStartTime()).isEqualTo("09:00");
        assertThat(resp.getEntries().get(1).getEndTime()).isEqualTo("10:30");
    }

    // ═══════════════════════════════════════════════════
    // Phase 5: Post-Processing
    // ═══════════════════════════════════════════════════

    @Test
    void generateSchedule_persistsEntries() {
        stubCommonRepos(MONDAY);
        Task task = buildTask("Task", 60, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void generateSchedule_responseIncludesTotalWorkMinutes() {
        stubCommonRepos(MONDAY);
        Task task = buildTask("Task", 120, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getTotalWorkMinutes()).isEqualTo(120);
    }

    @Test
    void generateSchedule_lastScheduledDaySetCorrectly() {
        stubCommonRepos(MONDAY);
        // 300min task = 240 Monday + 60 Tuesday
        Task task = buildTask("Task", 300, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getTo()).isEqualTo(MONDAY.plusDays(1)); // Tuesday
    }

    @Test
    void generateSchedule_defaultMaxDailyIs240() {
        stubCommonRepos(MONDAY);
        // Exactly 240min task should fit in 1 day with default maxDaily
        Task task = buildTask("Fits Exactly", 240, CognitiveLoad.MEDIUM);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY);
    }

    @Test
    void generateSchedule_budgetExceeded_skipsToNextDay() {
        stubCommonRepos(MONDAY);

        // Block Monday fully with a completed entry
        ScheduleEntry fullBlock = ScheduleEntry.builder()
                .entryDate(MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .entryType(ScheduleEntryType.TASK)
                .build();
        when(scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(userId, MONDAY))
                .thenReturn(List.of(fullBlock));

        Task task = buildTask("Task", 60, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(MONDAY.plusDays(1)); // Tuesday
    }

    @Test
    void generateSchedule_entryHasCorrectSourceType() {
        stubCommonRepos(MONDAY);
        Task task = buildTask("Task", 60, CognitiveLoad.LOW);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(task));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries().get(0).getSource())
                .isEqualTo(de.goaldone.backend.model.ScheduleEntry.SourceEnum.ONE_TIME);
    }

    @Test
    void generateSchedule_allTasksStartDateInFuture_advancesToThem() {
        stubCommonRepos(MONDAY);

        // Only task starts Wednesday
        LocalDate wednesday = MONDAY.plusDays(2);
        Task future = buildTask("Future Only", 60, CognitiveLoad.MEDIUM, null, wednesday);

        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(List.of(future));

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getDate()).isEqualTo(wednesday);
    }

    @Test
    void generateSchedule_generatedAtIsSet() {
        stubCommonRepos(MONDAY);
        when(taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(eq(userId), any()))
                .thenReturn(Collections.emptyList());

        ScheduleResponse resp = scheduleService.generateSchedule(userId, orgId, request(MONDAY));

        assertThat(resp.getGeneratedAt()).isNotNull();
    }
}
