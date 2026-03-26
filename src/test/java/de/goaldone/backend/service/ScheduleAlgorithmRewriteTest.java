package de.goaldone.backend.service;

import de.goaldone.backend.entity.*;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleAlgorithmRewriteTest {

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private BreakRepository breakRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkingHoursService workingHoursService;

    @InjectMocks
    private ScheduleService scheduleService;

    private User testUser;
    private Organization testOrg;
    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testOrg = Organization.builder().id(orgId).name("Test Org").build();
        testUser = User.builder().id(userId).email("test@example.com").organization(testOrg).build();
    }

    @Test
    @DisplayName("Should throw 400 when working hours are missing")
    void shouldThrow400WhenWorkingHoursMissing() {
        LocalDate from = LocalDate.of(2026, 3, 30);
        LocalDate to = from.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setTo(to);

        when(workingHoursService.getWorkingHours(userId)).thenThrow(new de.goaldone.backend.exception.ResourceNotFoundException("working-hours-not-found"));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> 
            scheduleService.generateSchedule(userId, orgId, request));
        
        assertEquals("working-hours-missing", ex.getReason());
    }

    @Test
    @DisplayName("Should throw 400 when window is not 14 days")
    void shouldThrow400WhenWindowInvalid() {
        LocalDate from = LocalDate.of(2026, 3, 30);
        LocalDate to = from.plusDays(10); // Not 13
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setTo(to);

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> 
            scheduleService.generateSchedule(userId, orgId, request));
        
        assertEquals("invalid-schedule-window", ex.getReason());
    }

    @Test
    @DisplayName("Should respect fixed and completed entries and subtract from budget")
    void shouldRespectFixedEntries() {
        LocalDate from = LocalDate.of(2026, 3, 30); // Monday
        LocalDate to = from.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setTo(to);
        request.setMaxDailyWorkMinutes(120); // 2 hours

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                .thenReturn(new ArrayList<>(List.of(
                        Task.builder().id(taskId).title("New Task").estimatedDurationMinutes(60).cognitiveLoad(CognitiveLoad.MEDIUM).status(TaskStatus.OPEN).owner(testUser).organization(testOrg).build()
                )));
        when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        
        // Mock a pinned entry on the first day that takes 90 minutes
        ScheduleEntry pinned = ScheduleEntry.builder()
                .entryDate(from)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .isPinned(true)
                .entryType(ScheduleEntryType.TASK)
                .build();
        
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to))
                .thenReturn(new ArrayList<>(List.of(pinned)));

        when(workingHoursService.getWorkWindow(userId, from)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

        // Act
        ScheduleResponse response = scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        // On day 1: budget 120 - 90 = 30 mins remaining. New task needs 60.
        // It should schedule 30 mins and warn.
        assertTrue(response.getWarnings().contains("task-budget-exceeded:" + taskId));
    }

    @Test
    @DisplayName("Should expand recurring tasks")
    void shouldExpandRecurringTasks() {
        LocalDate from = LocalDate.of(2026, 3, 30); // Monday
        LocalDate to = from.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setTo(to);
        request.setMaxDailyWorkMinutes(240);

        Task dailyTask = Task.builder()
                .id(UUID.randomUUID())
                .title("Daily Standup")
                .estimatedDurationMinutes(15)
                .cognitiveLoad(CognitiveLoad.LOW)
                .status(TaskStatus.OPEN)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startDate(from)
                .owner(testUser)
                .organization(testOrg)
                .build();

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                .thenReturn(new ArrayList<>(List.of(dailyTask)));
        when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to)).thenReturn(new ArrayList<>());
        
        // Mock work windows for all days
        for (int i = 0; i < 14; i++) {
            when(workingHoursService.getWorkWindow(userId, from.plusDays(i)))
                    .thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
        }

        // Act
        scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        List<ScheduleEntry> savedEntries = captor.getValue();

        // Should have 14 entries for the daily task
        assertEquals(14, savedEntries.size());
        for (ScheduleEntry entry : savedEntries) {
            assertEquals(dailyTask.getId(), entry.getTask().getId());
        }
    }

    @Test
    @DisplayName("Should respect startDate")
    void shouldRespectStartDate() {
        LocalDate from = LocalDate.of(2026, 3, 30); // Monday
        LocalDate to = from.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(from);
        request.setTo(to);
        request.setMaxDailyWorkMinutes(240);

        LocalDate startDate = from.plusDays(2); // Wednesday
        Task delayedTask = Task.builder()
                .id(UUID.randomUUID())
                .title("Delayed Task")
                .estimatedDurationMinutes(60)
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .status(TaskStatus.OPEN)
                .startDate(startDate)
                .owner(testUser)
                .organization(testOrg)
                .build();

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                .thenReturn(new ArrayList<>(List.of(delayedTask)));
        when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to)).thenReturn(new ArrayList<>());
        
        // Mock work windows
        when(workingHoursService.getWorkWindow(userId, from)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
        when(workingHoursService.getWorkWindow(userId, from.plusDays(1))).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
        when(workingHoursService.getWorkWindow(userId, startDate)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

        // Act
        scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        List<ScheduleEntry> savedEntries = captor.getValue();

        assertEquals(1, savedEntries.size());
        assertEquals(startDate, savedEntries.get(0).getEntryDate());
    }
}
