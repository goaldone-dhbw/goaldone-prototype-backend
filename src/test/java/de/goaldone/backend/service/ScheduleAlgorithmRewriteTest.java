package de.goaldone.backend.service;

import de.goaldone.backend.entity.*;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
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
    @DisplayName("Should schedule more than maxDailyWorkMinutes if window allows, with system breaks for HIGH tasks")
    void shouldHandleSystemBreaksAndExceedOldBudget() {
        // Arrange
        LocalDate today = LocalDate.of(2026, 3, 26);
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(today);
        request.setMaxDailyWorkMinutes(120); // 2 hours work block limit

        // One big HIGH task: 5 hours (300 mins)
        Task bigTask = Task.builder()
                .id(UUID.randomUUID())
                .title("Big High Task")
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.HIGH)
                .estimatedDurationMinutes(300)
                .owner(testUser)
                .organization(testOrg)
                .build();

        when(workingHoursService.getWorkingHours(userId)).thenReturn(new WorkingHoursResponse(List.of(new de.goaldone.backend.model.WorkingHoursDayEntry())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(any(), any())).thenReturn(new ArrayList<>(List.of(bigTask)));
        when(breakRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(scheduleEntryRepository.findByUserIdAndEntryDateBetween(any(), any(), any())).thenReturn(new ArrayList<>());
        
        // Window: 08:00 - 20:00 (12 hours)
        when(workingHoursService.getWorkWindow(any(), any()))
                .thenReturn(Optional.of(new WorkingHoursService.WorkWindow(LocalTime.of(8, 0), LocalTime.of(20, 0))));

        // Act
        scheduleService.generateSchedule(userId, orgId, request);

        // Assert
        ArgumentCaptor<List<ScheduleEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleEntryRepository).saveAll(captor.capture());
        
        List<ScheduleEntry> savedEntries = captor.getValue();
        
        // For one day, we expect:
        // 08:00 - 10:00: Work (120m)
        // 10:00 - 11:00: System Break (60m = 120/2)
        // 11:00 - 13:00: Work (120m)
        // 13:00 - 14:00: System Break (60m)
        // 14:00 - 15:00: Work (60m remaining)
        // Total work: 300m = 5h.
        
        List<ScheduleEntry> todayEntries = savedEntries.stream()
                .filter(e -> e.getEntryDate().equals(today))
                .sorted(Comparator.comparing(ScheduleEntry::getStartTime))
                .toList();
        
        long workMinutes = todayEntries.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.TASK)
                .mapToLong(e -> java.time.Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
                .sum();
        
        assertEquals(300, workMinutes, "Should have scheduled 300 minutes of work despite 120m block limit");
        
        // Check for the system breaks (they should be scheduled as BREAK entries)
        // System breaks are created as synthetic Break entities with label "System Break"
        long breakMinutes = todayEntries.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.BREAK)
                .mapToLong(e -> java.time.Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
                .sum();

        assertTrue(breakMinutes > 0, "Should have scheduled system breaks");
        assertEquals(120, breakMinutes, "Should have 2 system breaks of 60 minutes each (total 120 minutes)");
    }
}
