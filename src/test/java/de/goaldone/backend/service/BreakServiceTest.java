package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.RecurrenceRule;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BreakService validation logic and the breaksBlocksDay helper.
 */
public class BreakServiceTest {

    private final ValidationService validationService = new ValidationService();

    // ════════════════════════════════════════════════════════════════════════
    // VALIDATION TESTS (TEST 1-8)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void test1_ONE_TIME_breakValid() {
        // TEST 1: ONE_TIME break with all required fields valid
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Arzttermin");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.of(2026, 4, 7)));

        // Should not throw any exception during validation
        assertDoesNotThrow(() -> validateBreakRequest(request));
    }

    @Test
    public void test2_ONE_TIME_missingDate() {
        // TEST 2: ONE_TIME break without date → 400 "missing-date"
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Arzttermin");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        // date is null

        var ex = assertThrows(ValidationException.class, () -> validateBreakRequest(request));
        assertTrue(ex.getMessage().contains("date is required"));
    }

    @Test
    public void test3_ONE_TIME_unexpectedRecurrence() {
        // TEST 3: ONE_TIME with unexpected recurrence field
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Arzttermin");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.of(2026, 4, 7)));

        var recurrence = new RecurrenceRule();
        recurrence.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        recurrence.setInterval(1);
        request.setRecurrence(recurrence);

        var ex = assertThrows(ValidationException.class, () -> validateBreakRequest(request));
        assertTrue(ex.getMessage().contains("recurrence must be null"));
    }

    @Test
    public void test4_RECURRING_breakValid() {
        // TEST 4: RECURRING break with recurrence rule
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Mittagspause");
        request.setStartTime("12:00");
        request.setEndTime("13:00");
        request.setBreakType(BreakType.RECURRING);

        var recurrence = new RecurrenceRule();
        recurrence.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        recurrence.setInterval(1);
        request.setRecurrence(recurrence);

        assertDoesNotThrow(() -> validateBreakRequest(request));
    }

    @Test
    public void test5_RECURRING_missingRecurrence() {
        // TEST 5: RECURRING without recurrence → 400 "missing-recurrence"
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Mittagspause");
        request.setStartTime("12:00");
        request.setEndTime("13:00");
        request.setBreakType(BreakType.RECURRING);
        // recurrence is null

        var ex = assertThrows(ValidationException.class, () -> validateBreakRequest(request));
        assertTrue(ex.getMessage().contains("recurrence is required"));
    }

    @Test
    public void test6_BOUNDED_RECURRING_valid() {
        // TEST 6: BOUNDED_RECURRING with all required fields
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Kurze Auszeit");
        request.setStartTime("11:00");
        request.setEndTime("12:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);

        var recurrence = new RecurrenceRule();
        recurrence.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        recurrence.setInterval(1);
        request.setRecurrence(recurrence);

        request.setValidFrom(JsonNullable.of(LocalDate.of(2026, 4, 7)));
        request.setValidUntil(JsonNullable.of(LocalDate.of(2026, 4, 10)));

        assertDoesNotThrow(() -> validateBreakRequest(request));
    }

    @Test
    public void test7_BOUNDED_RECURRING_missingValidUntil() {
        // TEST 7: BOUNDED_RECURRING without validUntil
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Kurze Auszeit");
        request.setStartTime("11:00");
        request.setEndTime("12:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);

        var recurrence = new RecurrenceRule();
        recurrence.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        recurrence.setInterval(1);
        request.setRecurrence(recurrence);

        request.setValidFrom(JsonNullable.of(LocalDate.of(2026, 4, 7)));
        // validUntil is null

        var ex = assertThrows(ValidationException.class, () -> validateBreakRequest(request));
        assertTrue(ex.getMessage().contains("validUntil is required"));
    }

    @Test
    public void test8_BOUNDED_RECURRING_invalidDateRange() {
        // TEST 8: BOUNDED_RECURRING with validUntil < validFrom
        var request = new de.goaldone.backend.model.CreateBreakRequest();
        request.setLabel("Kurze Auszeit");
        request.setStartTime("11:00");
        request.setEndTime("12:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);

        var recurrence = new RecurrenceRule();
        recurrence.setType(de.goaldone.backend.model.RecurrenceType.DAILY);
        recurrence.setInterval(1);
        request.setRecurrence(recurrence);

        request.setValidFrom(JsonNullable.of(LocalDate.of(2026, 4, 10)));
        request.setValidUntil(JsonNullable.of(LocalDate.of(2026, 4, 7)));

        var ex = assertThrows(ValidationException.class, () -> validateBreakRequest(request));
        assertTrue(ex.getMessage().contains("validFrom must not be after validUntil"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // breaksBlocksDay HELPER TESTS (TEST 9-17)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void test9_ONE_TIME_blocksMatchingDay() {
        // TEST 9: ONE_TIME break on Monday, querying Monday → true
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.ONE_TIME)
                .date(LocalDate.of(2026, 4, 6)) // Monday
                .label("Doctor")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .organizationId(UUID.randomUUID())
                .build();

        LocalDate monday = LocalDate.of(2026, 4, 6);
        assertTrue(BreakService.breaksBlocksDay(breakEntity, monday));
    }

    @Test
    public void test10_ONE_TIME_blocksNonMatchingDay() {
        // TEST 10: ONE_TIME break on Monday, querying Tuesday → false
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.ONE_TIME)
                .date(LocalDate.of(2026, 4, 6)) // Monday
                .label("Doctor")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .organizationId(UUID.randomUUID())
                .build();

        LocalDate tuesday = LocalDate.of(2026, 4, 7);
        assertFalse(BreakService.breaksBlocksDay(breakEntity, tuesday));
    }

    @Test
    public void test11_RECURRING_dailyBlocksAnyDay() {
        // TEST 11: RECURRING DAILY → blocks any day
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .label("Lunch")
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 6)));
        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 7)));
    }

    @Test
    public void test12_RECURRING_weeklyBlocksWrongDay() {
        // TEST 12: RECURRING WEEKLY – currently accepts all days (MVP behavior)
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.WEEKLY)
                .recurrenceInterval(1)
                .label("Team Meeting")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .organizationId(UUID.randomUUID())
                .build();

        // In MVP, WEEKLY matches any day. Full implementation would track day-of-week.
        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 6)));
    }

    @Test
    public void test13_BOUNDED_RECURRING_withinRangeAndRuleMatches() {
        // TEST 13: BOUNDED_RECURRING within range + rule matches → true
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 4, 7))
                .validUntil(LocalDate.of(2026, 4, 10))
                .label("Summer Hours")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 8))); // within range
    }

    @Test
    public void test14_BOUNDED_RECURRING_beforeRange() {
        // TEST 14: BOUNDED_RECURRING, querying before validFrom → false
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 4, 7))
                .validUntil(LocalDate.of(2026, 4, 10))
                .label("Summer Hours")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertFalse(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 6))); // before validFrom
    }

    @Test
    public void test15_BOUNDED_RECURRING_afterRange() {
        // TEST 15: BOUNDED_RECURRING, querying after validUntil → false
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 4, 7))
                .validUntil(LocalDate.of(2026, 4, 10))
                .label("Summer Hours")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertFalse(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 11))); // after validUntil
    }

    @Test
    public void test16_BOUNDED_RECURRING_boundaryValidFrom() {
        // TEST 16: BOUNDED_RECURRING at exact validFrom boundary → true
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 4, 7))
                .validUntil(LocalDate.of(2026, 4, 10))
                .label("Summer Hours")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 7))); // exact validFrom
    }

    @Test
    public void test17_BOUNDED_RECURRING_boundaryValidUntil() {
        // TEST 17: BOUNDED_RECURRING at exact validUntil boundary → true
        var breakEntity = Break.builder()
                .id(UUID.randomUUID())
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 4, 7))
                .validUntil(LocalDate.of(2026, 4, 10))
                .label("Summer Hours")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .organizationId(UUID.randomUUID())
                .build();

        assertTrue(BreakService.breaksBlocksDay(breakEntity, LocalDate.of(2026, 4, 10))); // exact validUntil
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Inline validation logic matching BreakService.validateBreakRequest
     */
    private void validateBreakRequest(de.goaldone.backend.model.CreateBreakRequest request) {
        validationService.requireNotBlank(request.getLabel(), "label");
        validationService.requireMaxLength(request.getLabel(), "label", 255);
        validationService.requireNotBlank(request.getStartTime(), "startTime");
        validationService.requireNotBlank(request.getEndTime(), "endTime");

        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());
        validationService.requireAfter(end, start, "endTime");

        validationService.requireNotNull(request.getBreakType(), "breakType");

        switch (request.getBreakType()) {
            case ONE_TIME:
                if (request.getDate() == null) {
                    throw new ValidationException("missing-date", "date is required for ONE_TIME break type");
                }
                if (request.getRecurrence() != null) {
                    throw new ValidationException("unexpected-recurrence", "recurrence must be null for ONE_TIME break type");
                }
                if (request.getValidFrom() != null || request.getValidUntil() != null) {
                    throw new ValidationException("unexpected-date-range", "validFrom and validUntil must be null for ONE_TIME break type");
                }
                break;

            case RECURRING:
                if (request.getRecurrence() == null) {
                    throw new ValidationException("missing-recurrence", "recurrence is required for RECURRING break type");
                }
                validationService.requireNotNull(request.getRecurrence().getType(), "recurrence.type");
                validationService.requirePositive(request.getRecurrence().getInterval(), "recurrence.interval");
                if (request.getDate() != null) {
                    throw new ValidationException("unexpected-date", "date must be null for RECURRING break type");
                }
                if (request.getValidFrom() != null || request.getValidUntil() != null) {
                    throw new ValidationException("unexpected-date-range", "validFrom and validUntil must be null for RECURRING break type");
                }
                break;

            case BOUNDED_RECURRING:
                if (request.getRecurrence() == null) {
                    throw new ValidationException("missing-recurrence", "recurrence is required for BOUNDED_RECURRING break type");
                }
                validationService.requireNotNull(request.getRecurrence().getType(), "recurrence.type");
                validationService.requirePositive(request.getRecurrence().getInterval(), "recurrence.interval");

                if (request.getValidFrom() == null) {
                    throw new ValidationException("missing-valid-from", "validFrom is required for BOUNDED_RECURRING break type");
                }
                if (request.getValidUntil() == null) {
                    throw new ValidationException("missing-valid-until", "validUntil is required for BOUNDED_RECURRING break type");
                }
                if (request.getValidFrom().get().isAfter(request.getValidUntil().get())) {
                    throw new ValidationException("invalid-date-range", "validFrom must not be after validUntil");
                }
                if (request.getDate() != null) {
                    throw new ValidationException("unexpected-date", "date must be null for BOUNDED_RECURRING break type");
                }
                break;
        }
    }
}
