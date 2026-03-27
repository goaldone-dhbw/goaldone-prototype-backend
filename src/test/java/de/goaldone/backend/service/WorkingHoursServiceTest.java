package de.goaldone.backend.service;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.WorkingHourEntry;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingHoursServiceTest {

    @Mock
    private WorkingHourEntryRepository workingHourEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ValidationService validationService;

    @InjectMocks
    private WorkingHoursService workingHoursService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("test@example.com").build();
    }

    @Test
    @DisplayName("Should correctly identify work day")
    void shouldIdentifyWorkDay() {
        // Arrange
        LocalDate monday = LocalDate.of(2026, 3, 30);
        when(workingHourEntryRepository.findByUserId(userId)).thenReturn(List.of(
                WorkingHourEntry.builder().dayOfWeek(DayOfWeek.MONDAY).workDay(true).build(),
                WorkingHourEntry.builder().dayOfWeek(DayOfWeek.SUNDAY).workDay(false).build()
        ));

        // Act & Assert
        assertTrue(workingHoursService.isWorkDay(userId, monday));
        assertFalse(workingHoursService.isWorkDay(userId, monday.plusDays(6))); // Sunday
    }

    @Test
    @DisplayName("Should return correct work window")
    void shouldReturnWorkWindow() {
        // Arrange
        LocalDate monday = LocalDate.of(2026, 3, 30);
        LocalTime startTime = LocalTime.of(8, 30);
        LocalTime endTime = LocalTime.of(17, 30);
        
        when(workingHourEntryRepository.findByUserId(userId)).thenReturn(List.of(
                WorkingHourEntry.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .workDay(true)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build()
        ));

        // Act
        Optional<WorkingHoursService.WorkWindow> window = workingHoursService.getWorkWindow(userId, monday);

        // Assert
        assertTrue(window.isPresent());
        assertEquals(startTime, window.get().startTime());
        assertEquals(endTime, window.get().endTime());
    }

    @Test
    @DisplayName("Should return empty for non-work day")
    void shouldReturnEmptyForNonWorkDay() {
        // Arrange
        LocalDate sunday = LocalDate.of(2026, 4, 5);
        when(workingHourEntryRepository.findByUserId(userId)).thenReturn(List.of(
                WorkingHourEntry.builder().dayOfWeek(DayOfWeek.SUNDAY).workDay(false).build()
        ));

        // Act
        Optional<WorkingHoursService.WorkWindow> window = workingHoursService.getWorkWindow(userId, sunday);

        // Assert
        assertTrue(window.isEmpty());
    }
}
