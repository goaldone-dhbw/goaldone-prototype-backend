package de.goaldone.backend.service;

import de.goaldone.backend.entity.*;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.model.GenerateScheduleRequest;
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
class ScheduleCollisionFixTest {

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
    @DisplayName("Should not schedule break if a pinned entry already exists at the same start time")
    void shouldNotScheduleBreakOnPinnedEntryCollision() {
        // Arrange
        LocalDate today = LocalDate.of(2026, 4, 7);
        LocalDate to = today.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(today);
        request.setTo(to);
        request.setMaxDailyWorkMinutes(240);

        // Fixed entry at 09:00
        ScheduleEntry pinnedEntry = ScheduleEntry.builder()
                .user(testUser)
                .organization(testOrg)
                .entryDate(today)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .entryType(ScheduleEntryType.TASK)
                .isPinned(true)
                .build();

        // Recurring break also at 09:00
        Break morningBreak = Break.builder()
                .id(UUID.randomUUID())
                .label("Coffee")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 15))
                .user(testUser)
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any())).thenReturn(new ArrayList<>());
        when(breakRepository.findByUserId(userId)).thenReturn(List.of(morningBreak));
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, today, to)).thenReturn(List.of(pinnedEntry));
        when(workingHoursService.getWorkWindow(userId, today)).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

        // Act
        scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        
        List<ScheduleEntry> savedEntries = captor.getValue();
        // The break at 09:00 should have been skipped to avoid collision with the pinned entry
        boolean hasBreakAtNine = savedEntries.stream()
                .anyMatch(e -> e.getEntryType() == ScheduleEntryType.BREAK && e.getStartTime().equals(LocalTime.of(9, 0)));
        
        assertFalse(hasBreakAtNine, "Should NOT have scheduled a break at 09:00 because it collides with a pinned entry");
    }

    @Test
    @DisplayName("Should not schedule multiple breaks at the same start time")
    void shouldNotScheduleDuplicateBreaks() {
        // Arrange
        LocalDate today = LocalDate.of(2026, 4, 7);
        LocalDate to = today.plusDays(13);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(today);
        request.setTo(to);

        // Two breaks at 09:00
        Break break1 = Break.builder()
                .id(UUID.randomUUID())
                .label("Coffee 1")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 15))
                .user(testUser)
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();
        Break break2 = Break.builder()
                .id(UUID.randomUUID())
                .label("Coffee 2")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 15))
                .user(testUser)
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any())).thenReturn(new ArrayList<>());
        when(breakRepository.findByUserId(userId)).thenReturn(List.of(break1, break2));
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, today, to)).thenReturn(new ArrayList<>());
        when(workingHoursService.getWorkWindow(any(), any())).thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));

        // Act
        scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        
        List<ScheduleEntry> savedEntries = captor.getValue();
        long breaksAtNine = savedEntries.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.BREAK && e.getStartTime().equals(LocalTime.of(9, 0)))
                .count();
        
        assertEquals(14, breaksAtNine, "Should only have one break at 09:00 for each of the 14 days even if two are defined");
    }
}
