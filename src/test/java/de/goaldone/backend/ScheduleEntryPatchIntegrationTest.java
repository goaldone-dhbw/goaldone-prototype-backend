package de.goaldone.backend;

import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleEntryPatchIntegrationTest {

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private final UUID testEntryId = UUID.randomUUID();
    private final UUID testUserId = UUID.randomUUID();

    private de.goaldone.backend.entity.ScheduleEntry createTestEntry() {
        de.goaldone.backend.entity.ScheduleEntry entity = new de.goaldone.backend.entity.ScheduleEntry();
        entity.setId(testEntryId);
        entity.setEntryDate(LocalDate.now());
        entity.setStartTime(LocalTime.of(9, 0));
        entity.setEndTime(LocalTime.of(10, 0));
        entity.setEntryType(ScheduleEntryType.BREAK);
        entity.setGeneratedAt(Instant.now());
        return entity;
    }

    // ===== completeScheduleEntry Tests =====

    @Test
    void completeScheduleEntry_HappyPath_ReturnsCompleted() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = createTestEntry();
        entity.setCompleted(false);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));
        when(scheduleEntryRepository.save(any())).thenReturn(entity);

        // Act
        ScheduleEntry result = scheduleService.completeScheduleEntry(testEntryId, testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(testEntryId, result.getId());
        assertTrue(result.getIsCompleted());
        verify(scheduleEntryRepository).findByIdAndUserId(testEntryId, testUserId);
        verify(scheduleEntryRepository).save(any());
    }

    @Test
    void completeScheduleEntry_AlreadyCompleted_ThrowsConflict() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = new de.goaldone.backend.entity.ScheduleEntry();
        entity.setId(testEntryId);
        entity.setCompleted(true);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> scheduleService.completeScheduleEntry(testEntryId, testUserId));
        assertEquals("schedule-entry-already-completed", exception.getMessage());
        verify(scheduleEntryRepository, never()).save(any());
    }

    @Test
    void completeScheduleEntry_NotFound_ThrowsNotFound() {
        // Arrange
        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> scheduleService.completeScheduleEntry(testEntryId, testUserId));
    }

    // ===== pinScheduleEntry Tests =====

    @Test
    void pinScheduleEntry_HappyPath_ReturnsPinned() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = createTestEntry();
        entity.setPinned(false);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));
        when(scheduleEntryRepository.save(any())).thenReturn(entity);

        // Act
        ScheduleEntry result = scheduleService.pinScheduleEntry(testEntryId, testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(testEntryId, result.getId());
        assertTrue(result.getIsPinned());
        verify(scheduleEntryRepository).findByIdAndUserId(testEntryId, testUserId);
        verify(scheduleEntryRepository).save(any());
    }

    @Test
    void pinScheduleEntry_AlreadyPinned_ThrowsConflict() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = new de.goaldone.backend.entity.ScheduleEntry();
        entity.setId(testEntryId);
        entity.setPinned(true);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> scheduleService.pinScheduleEntry(testEntryId, testUserId));
        assertEquals("schedule-entry-already-pinned", exception.getMessage());
        verify(scheduleEntryRepository, never()).save(any());
    }

    // ===== unpinScheduleEntry Tests =====

    @Test
    void unpinScheduleEntry_HappyPath_ReturnsUnpinned() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = createTestEntry();
        entity.setPinned(true);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));
        when(scheduleEntryRepository.save(any())).thenReturn(entity);

        // Act
        ScheduleEntry result = scheduleService.unpinScheduleEntry(testEntryId, testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(testEntryId, result.getId());
        assertFalse(result.getIsPinned());
        verify(scheduleEntryRepository).findByIdAndUserId(testEntryId, testUserId);
        verify(scheduleEntryRepository).save(any());
    }

    @Test
    void unpinScheduleEntry_NotPinned_ThrowsConflict() {
        // Arrange
        de.goaldone.backend.entity.ScheduleEntry entity = new de.goaldone.backend.entity.ScheduleEntry();
        entity.setId(testEntryId);
        entity.setPinned(false);

        when(scheduleEntryRepository.findByIdAndUserId(testEntryId, testUserId))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> scheduleService.unpinScheduleEntry(testEntryId, testUserId));
        assertEquals("schedule-entry-not-pinned", exception.getMessage());
        verify(scheduleEntryRepository, never()).save(any());
    }
}
