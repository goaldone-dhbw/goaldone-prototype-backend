package de.goaldone.backend.service;

import de.goaldone.backend.entity.*;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class ScheduleServiceTest {

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private BreakRepository breakRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ValidationService validationService;
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

    @Nested
    @DisplayName("Generate Schedule Tests")
    class GenerateScheduleTests {

        @Test
        @DisplayName("Should generate schedule with simple tasks and no breaks")
        void shouldGenerateScheduleWithSimpleTasks() {
            // Arrange
            LocalDate from = LocalDate.of(2026, 3, 30); // Monday
            LocalDate to = from.plusDays(13); // 14 days
            GenerateScheduleRequest request = new GenerateScheduleRequest();
            request.setFrom(from);
            request.setTo(to);
            request.setMaxDailyWorkMinutes(240);

            when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                    .thenReturn(new ArrayList<>(List.of(
                            Task.builder().id(UUID.randomUUID()).title("Task 1").estimatedDurationMinutes(60).cognitiveLoad(CognitiveLoad.MEDIUM).status(TaskStatus.OPEN).owner(testUser).organization(testOrg).build(),
                            Task.builder().id(UUID.randomUUID()).title("Task 2").estimatedDurationMinutes(120).cognitiveLoad(CognitiveLoad.HIGH).status(TaskStatus.OPEN).owner(testUser).organization(testOrg).build()
                    )));
            when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
            when(workingHoursService.getWorkWindow(userId, from)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

            // Act
            scheduleService.generateSchedule(userId, orgId, request);

            // Assert
            verify(scheduleEntryRepository).deleteByUserIdAndEntryDateBetweenAndIsCompletedFalseAndIsPinnedFalse(userId, from, to);
            
            ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleEntryRepository).saveAll(captor.capture());
            
            List<ScheduleEntry> savedEntries = captor.getValue();
            // Should be sorted by HIGH cognitive load first (Task 2 before Task 1)
            assertEquals("Task 2", savedEntries.get(0).getTask().getTitle());
            assertEquals(LocalTime.of(9, 0), savedEntries.get(0).getStartTime());
            assertEquals(LocalTime.of(11, 0), savedEntries.get(0).getEndTime());
            
            assertEquals("Task 1", savedEntries.get(1).getTask().getTitle());
            assertEquals(LocalTime.of(11, 0), savedEntries.get(1).getStartTime());
            assertEquals(LocalTime.of(12, 0), savedEntries.get(1).getEndTime());
        }

        @Test
        @DisplayName("Should split task if it exceeds daily capacity")
        void shouldSplitTaskExceedingCapacity() {
            // Arrange
            LocalDate monday = LocalDate.of(2026, 3, 30);
            LocalDate to = monday.plusDays(13);
            GenerateScheduleRequest request = new GenerateScheduleRequest();
            request.setFrom(monday);
            request.setTo(to);
            request.setMaxDailyWorkMinutes(120); // 2 hours capacity

            Task longTask = Task.builder()
                    .id(UUID.randomUUID())
                    .title("Long Task")
                    .estimatedDurationMinutes(180) // 3 hours
                    .cognitiveLoad(CognitiveLoad.MEDIUM)
                    .status(TaskStatus.OPEN)
                    .owner(testUser)
                    .organization(testOrg)
                    .build();

            when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                    .thenReturn(new ArrayList<>(List.of(longTask)));
            when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
            when(workingHoursService.getWorkWindow(userId, monday)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
            when(workingHoursService.getWorkWindow(userId, monday.plusDays(1))).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

            // Act
            scheduleService.generateSchedule(userId, orgId, request);

            // Assert
            ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleEntryRepository).saveAll(captor.capture());
            
            List<ScheduleEntry> savedEntries = captor.getValue();
            assertEquals(2, savedEntries.size());
            
            assertEquals("Long Task", savedEntries.get(0).getTask().getTitle());
            assertEquals(monday, savedEntries.get(0).getEntryDate());
            assertEquals(120, java.time.Duration.between(savedEntries.get(0).getStartTime(), savedEntries.get(0).getEndTime()).toMinutes());

            assertEquals("Long Task", savedEntries.get(1).getTask().getTitle());
            assertEquals(monday.plusDays(1), savedEntries.get(1).getEntryDate());
            assertEquals(60, java.time.Duration.between(savedEntries.get(1).getStartTime(), savedEntries.get(1).getEndTime()).toMinutes());
        }

        @Test
        @DisplayName("Should schedule around breaks")
        void shouldScheduleAroundBreaks() {
            // Arrange
            LocalDate monday = LocalDate.of(2026, 3, 30);
            LocalDate to = monday.plusDays(13);
            GenerateScheduleRequest request = new GenerateScheduleRequest();
            request.setFrom(monday);
            request.setTo(to);
            request.setMaxDailyWorkMinutes(240);

            Break lunchBreak = Break.builder()
                    .id(UUID.randomUUID())
                    .label("Lunch")
                    .startTime(LocalTime.of(12, 0))
                    .endTime(LocalTime.of(13, 0))
                    .user(testUser)
                    .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                    .recurrenceType(RecurrenceType.DAILY)
                    .recurrenceInterval(1)
                    .build();

            Task task = Task.builder()
                    .id(UUID.randomUUID())
                    .title("Task 1")
                    .estimatedDurationMinutes(240) // 4 hours
                    .cognitiveLoad(CognitiveLoad.MEDIUM)
                    .status(TaskStatus.OPEN)
                    .owner(testUser)
                    .organization(testOrg)
                    .build();

            when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                    .thenReturn(new ArrayList<>(List.of(task)));
            when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>(List.of(lunchBreak)));
            when(workingHoursService.getWorkWindow(userId, monday)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

            // Act
            scheduleService.generateSchedule(userId, orgId, request);

            // Assert
            ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleEntryRepository).saveAll(captor.capture());
            
            List<ScheduleEntry> entries = captor.getValue();
            // Should have 1 break + 2 blocks for the task (split around break)
            // Wait, the new logic adds breaks to newEntries too.
            assertTrue(entries.stream().anyMatch(e -> e.getEntryType() == ScheduleEntryType.BREAK));
            
            List<ScheduleEntry> taskEntries = entries.stream().filter(e -> e.getEntryType() == ScheduleEntryType.TASK).toList();
            assertEquals(2, taskEntries.size());
            
            assertEquals(LocalTime.of(9, 0), taskEntries.get(0).getStartTime());
            assertEquals(LocalTime.of(12, 0), taskEntries.get(0).getEndTime());

            assertEquals(LocalTime.of(13, 0), taskEntries.get(1).getStartTime());
            assertEquals(LocalTime.of(14, 0), taskEntries.get(1).getEndTime());
        }

        @Test
        @DisplayName("Should split 8h task into two 4h blocks across two days")
        void shouldSplit8hTaskIntoTwo4hBlocks() {
            // Arrange
            LocalDate monday = LocalDate.of(2026, 3, 30);
            LocalDate to = monday.plusDays(13);
            GenerateScheduleRequest request = new GenerateScheduleRequest();
            request.setFrom(monday);
            request.setTo(to);
            request.setMaxDailyWorkMinutes(240); // 4 hours capacity

            Task longTask = Task.builder()
                    .id(UUID.randomUUID())
                    .title("8h Task")
                    .estimatedDurationMinutes(480) // 8 hours
                    .cognitiveLoad(CognitiveLoad.MEDIUM)
                    .status(TaskStatus.OPEN)
                    .owner(testUser)
                    .organization(testOrg)
                    .build();

            when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any()))
                    .thenReturn(new ArrayList<>(List.of(longTask)));
            when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
            when(workingHoursService.getWorkWindow(userId, monday)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
            when(workingHoursService.getWorkWindow(userId, monday.plusDays(1))).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

            // Act
            scheduleService.generateSchedule(userId, orgId, request);

            // Assert
            ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleEntryRepository).saveAll(captor.capture());
            
            List<ScheduleEntry> savedEntries = captor.getValue();
            assertEquals(2, savedEntries.size());
            assertEquals(240, java.time.Duration.between(savedEntries.get(0).getStartTime(), savedEntries.get(0).getEndTime()).toMinutes());
            assertEquals(240, java.time.Duration.between(savedEntries.get(1).getStartTime(), savedEntries.get(1).getEndTime()).toMinutes());
        }
    }
}
