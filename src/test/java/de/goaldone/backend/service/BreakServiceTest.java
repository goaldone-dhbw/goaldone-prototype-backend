package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreakServiceTest {

    @InjectMocks
    private BreakService breakService;

    @Mock
    private BreakRepository breakRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ValidationService validationService;

    // ── createBreak Tests ────────────────────────────────────────────────────

    @Test
    void createBreak_oneTime_happyPath() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Doctor Appointment");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(org.openapitools.jackson.nullable.JsonNullable.of(LocalDate.of(2026, 4, 15)));

        var user = new de.goaldone.backend.entity.User();
        user.setId(java.util.UUID.randomUUID());
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(user));
        when(breakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = breakService.createBreak(request, user.getId(), java.util.UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.getLabel()).isEqualTo("Doctor Appointment");
        assertThat(response.getBreakType()).isEqualTo(BreakType.ONE_TIME);
    }

    @Test
    void createBreak_oneTime_missingDate_throwsError() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Doctor Appointment");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        // date is null

        assertThatThrownBy(() -> breakService.createBreak(request, java.util.UUID.randomUUID(), java.util.UUID.randomUUID()))
                .isInstanceOf(de.goaldone.backend.exception.ValidationException.class)
                .hasMessageStartingWith("missing-date:");
    }

    @Test
    void createBreak_recurring_happyPath() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Daily Lunch");
        request.setStartTime("12:00");
        request.setEndTime("13:00");
        request.setBreakType(BreakType.RECURRING);
        var rule = new de.goaldone.backend.model.RecurrenceRule();
        rule.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrence(rule);

        var user = new de.goaldone.backend.entity.User();
        user.setId(java.util.UUID.randomUUID());
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(user));
        when(breakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = breakService.createBreak(request, user.getId(), java.util.UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.getBreakType()).isEqualTo(BreakType.RECURRING);
    }

    @Test
    void createBreak_recurring_missingRecurrence_throwsError() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Daily Lunch");
        request.setStartTime("12:00");
        request.setEndTime("13:00");
        request.setBreakType(BreakType.RECURRING);
        // recurrence is null

        assertThatThrownBy(() -> breakService.createBreak(request, java.util.UUID.randomUUID(), java.util.UUID.randomUUID()))
                .isInstanceOf(de.goaldone.backend.exception.ValidationException.class)
                .hasMessageStartingWith("missing-recurrence:");
    }

    @Test
    void createBreak_boundedRecurring_happyPath() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Summer Break");
        request.setStartTime("14:00");
        request.setEndTime("15:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);
        var rule = new de.goaldone.backend.model.RecurrenceRule();
        rule.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrence(rule);
        request.setValidFrom(org.openapitools.jackson.nullable.JsonNullable.of(LocalDate.of(2026, 6, 1)));
        request.setValidUntil(org.openapitools.jackson.nullable.JsonNullable.of(LocalDate.of(2026, 8, 31)));

        var user = new de.goaldone.backend.entity.User();
        user.setId(java.util.UUID.randomUUID());
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(user));
        when(breakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = breakService.createBreak(request, user.getId(), java.util.UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.getBreakType()).isEqualTo(BreakType.BOUNDED_RECURRING);
    }

    @Test
    void createBreak_boundedRecurring_missingValidFrom_throwsError() {
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Summer Break");
        request.setStartTime("14:00");
        request.setEndTime("15:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);
        var rule = new de.goaldone.backend.model.RecurrenceRule();
        rule.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrence(rule);
        request.setValidUntil(org.openapitools.jackson.nullable.JsonNullable.of(LocalDate.of(2026, 8, 31)));
        // validFrom is null

        assertThatThrownBy(() -> breakService.createBreak(request, java.util.UUID.randomUUID(), java.util.UUID.randomUUID()))
                .isInstanceOf(de.goaldone.backend.exception.ValidationException.class)
                .hasMessageStartingWith("missing-valid-from:");
    }

    // ── breaksBlocksDay Tests ────────────────────────────────────────────────

    @Test
    void breaksBlocksDay_oneTime_sameDay_returnsTrue() {
        LocalDate day = LocalDate.of(2026, 4, 10);
        Break b = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(day)
                .build();

        assertThat(BreakService.breaksBlocksDay(b, day)).isTrue();
    }

    @Test
    void breaksBlocksDay_oneTime_differentDay_returnsFalse() {
        Break b = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(LocalDate.of(2026, 4, 10))
                .build();

        assertThat(BreakService.breaksBlocksDay(b, LocalDate.of(2026, 4, 11))).isFalse();
    }

    @Test
    void breaksBlocksDay_recurring_daily_anyDay_returnsTrue() {
        Break b = Break.builder()
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        LocalDate day1 = LocalDate.of(2026, 4, 10);
        LocalDate day2 = LocalDate.of(2026, 5, 1);

        assertThat(BreakService.breaksBlocksDay(b, day1)).isTrue();
        assertThat(BreakService.breaksBlocksDay(b, day2)).isTrue();
    }

    @Test
    void breaksBlocksDay_recurring_weekly_sameDay_returnsTrue() {
        // Monday, April 6, 2026
        LocalDate monday = LocalDate.of(2026, 4, 6);
        Break b = Break.builder()
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.WEEKLY)
                .recurrenceInterval(1)
                .build();

        // Next Monday (same day of week)
        LocalDate nextMonday = LocalDate.of(2026, 4, 13);
        assertThat(BreakService.breaksBlocksDay(b, nextMonday)).isTrue();
    }

    @Test
    void breaksBlocksDay_recurring_weekly_anyDay_returnsTrue() {
        // MVP: WEEKLY matches any day (placeholder implementation)
        // Monday, April 6, 2026
        LocalDate monday = LocalDate.of(2026, 4, 6);
        Break b = Break.builder()
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.WEEKLY)
                .recurrenceInterval(1)
                .build();

        // Tuesday (MVP returns true for any day for WEEKLY)
        LocalDate tuesday = LocalDate.of(2026, 4, 7);
        assertThat(BreakService.breaksBlocksDay(b, tuesday)).isTrue();
    }

    @Test
    void breaksBlocksDay_boundedRecurring_withinRange_returnsTrue() {
        LocalDate validFrom = LocalDate.of(2026, 4, 1);
        LocalDate validUntil = LocalDate.of(2026, 4, 30);
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        LocalDate withinRange = LocalDate.of(2026, 4, 15);
        assertThat(BreakService.breaksBlocksDay(b, withinRange)).isTrue();
    }

    @Test
    void breaksBlocksDay_boundedRecurring_beforeRange_returnsFalse() {
        LocalDate validFrom = LocalDate.of(2026, 4, 1);
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(validFrom)
                .validUntil(LocalDate.of(2026, 4, 30))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        LocalDate beforeRange = LocalDate.of(2026, 3, 31);
        assertThat(BreakService.breaksBlocksDay(b, beforeRange)).isFalse();
    }

    @Test
    void breaksBlocksDay_boundedRecurring_afterRange_returnsFalse() {
        LocalDate validUntil = LocalDate.of(2026, 4, 30);
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(LocalDate.of(2026, 4, 1))
                .validUntil(validUntil)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        LocalDate afterRange = LocalDate.of(2026, 5, 1);
        assertThat(BreakService.breaksBlocksDay(b, afterRange)).isFalse();
    }

    @Test
    void breaksBlocksDay_boundedRecurring_onValidFromBoundary_returnsTrue() {
        LocalDate validFrom = LocalDate.of(2026, 4, 1);
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(validFrom)
                .validUntil(LocalDate.of(2026, 4, 30))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        assertThat(BreakService.breaksBlocksDay(b, validFrom)).isTrue();
    }

    @Test
    void breaksBlocksDay_boundedRecurring_onValidUntilBoundary_returnsTrue() {
        LocalDate validUntil = LocalDate.of(2026, 4, 30);
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(LocalDate.of(2026, 4, 1))
                .validUntil(validUntil)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .build();

        assertThat(BreakService.breaksBlocksDay(b, validUntil)).isTrue();
    }
}
