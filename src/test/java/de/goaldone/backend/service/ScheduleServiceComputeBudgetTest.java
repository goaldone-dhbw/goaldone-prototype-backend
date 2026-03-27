package de.goaldone.backend.service;

import de.goaldone.backend.entity.*;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.RecurringExceptionType;
import de.goaldone.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduleService.computeBudget(day, max, completed, pinned, breaks, templates)
 * Tests the lazy budget calculation with all blocker types.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceComputeBudgetTest {

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private BreakRepository breakRepository;

    @Mock
    private RecurringTemplateRepository recurringTemplateRepository;

    @Mock
    private RecurringExceptionRepository recurringExceptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkingHoursService workingHoursService;

    @InjectMocks
    private ScheduleService scheduleService;

    private LocalDate testDay;
    private int maxDailyWorkMinutes = 240;
    private List<ScheduleEntry> completedEntries;
    private List<ScheduleEntry> pinnedEntries;
    private List<Break> userBreaks;
    private List<RecurringTemplate> userTemplates;
    private Map<String, RecurringException> exceptionsMap;

    @BeforeEach
    void setUp() {
        testDay = LocalDate.of(2026, 3, 27); // Friday
        completedEntries = new ArrayList<>();
        pinnedEntries = new ArrayList<>();
        userBreaks = new ArrayList<>();
        userTemplates = new ArrayList<>();
        exceptionsMap = new HashMap<>();
    }

    private ScheduleEntry createEntry(int startHour, int durationMinutes) {
        return ScheduleEntry.builder()
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(startHour, 0).plusMinutes(durationMinutes))
                .entryDate(testDay)
                .entryType(ScheduleEntryType.TASK)
                .build();
    }

    // Test 1: No blockers → returns max
    @Test
    void computeBudget_noBlockers_returnsMax() {
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 2: Completed entry same day → deducts duration
    @Test
    void computeBudget_completedEntrySameDay_deductsDuration() {
        completedEntries.add(createEntry(9, 60));
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 60);
    }

    // Test 3: Completed entry different day → no deduction
    @Test
    void computeBudget_completedEntryDifferentDay_noDeduction() {
        ScheduleEntry entry = createEntry(9, 60);
        entry.setEntryDate(testDay.minusDays(1));
        completedEntries.add(entry);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 4: Pinned entry same day → deducts duration
    @Test
    void computeBudget_pinnedEntrySameDay_deductsDuration() {
        pinnedEntries.add(createEntry(14, 30));
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 30);
    }

    // Test 5: Pinned entry different day → no deduction
    @Test
    void computeBudget_pinnedEntryDifferentDay_noDeduction() {
        ScheduleEntry entry = createEntry(14, 30);
        entry.setEntryDate(testDay.plusDays(1));
        pinnedEntries.add(entry);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 6: Break ONE_TIME same day → deducts duration
    @Test
    void computeBudget_breakOneTimeSameDay_deductsDuration() {
        Break b = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(testDay)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(12, 30))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 30);
    }

    // Test 7: Break ONE_TIME different day → no deduction
    @Test
    void computeBudget_breakOneTimeDifferentDay_noDeduction() {
        Break b = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(testDay.minusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(12, 30))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 8: Break RECURRING daily → deducts duration
    @Test
    void computeBudget_breakRecurringDaily_deductsDuration() {
        Break b = Break.builder()
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(15, 15))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 15);
    }

    // Test 9: Break BOUNDED_RECURRING within range → deducts duration
    @Test
    void computeBudget_breakBoundedRecurringWithinRange_deductsDuration() {
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(testDay.minusDays(5))
                .validUntil(testDay.plusDays(5))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 20))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 20);
    }

    // Test 10: Break BOUNDED_RECURRING before range → no deduction
    @Test
    void computeBudget_breakBoundedRecurringBeforeRange_noDeduction() {
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(testDay.minusDays(10))
                .validUntil(testDay.minusDays(2))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 20))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 11: Break BOUNDED_RECURRING after range → no deduction
    @Test
    void computeBudget_breakBoundedRecurringAfterRange_noDeduction() {
        Break b = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .validFrom(testDay.plusDays(2))
                .validUntil(testDay.plusDays(10))
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 20))
                .build();
        userBreaks.add(b);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 12: Template without exception → deducts duration
    @Test
    void computeBudget_templateNoException_deductsDuration() {
        RecurringTemplate t = RecurringTemplate.builder()
                .id(UUID.randomUUID())
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .durationMinutes(45)
                .createdAt(LocalDate.of(2026, 1, 1).atStartOfDay())
                .build();
        userTemplates.add(t);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 45);
    }

    // Test 13: Template with SKIPPED exception → no deduction
    @Test
    void computeBudget_templateSkippedException_noDeduction() {
        UUID templateId = UUID.randomUUID();
        RecurringTemplate t = RecurringTemplate.builder()
                .id(templateId)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .durationMinutes(45)
                .createdAt(LocalDate.of(2026, 1, 1).atStartOfDay())
                .build();
        userTemplates.add(t);
        RecurringException ex = RecurringException.builder()
                .type(RecurringExceptionType.SKIPPED)
                .occurrenceDate(testDay)
                .build();
        exceptionsMap.put(templateId + "_" + testDay, ex);
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes);
    }

    // Test 14: Multiple blockers combined → sums correctly
    @Test
    void computeBudget_multipleBlockers_sumCorrectly() {
        completedEntries.add(createEntry(9, 60));
        pinnedEntries.add(createEntry(11, 30));
        userBreaks.add(Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(testDay)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(12, 15))
                .build());
        RecurringTemplate t = RecurringTemplate.builder()
                .id(UUID.randomUUID())
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .durationMinutes(30)
                .createdAt(LocalDate.of(2026, 1, 1).atStartOfDay())
                .build();
        userTemplates.add(t);

        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(maxDailyWorkMinutes - 60 - 30 - 15 - 30);
    }

    // Test 15: Blockers exceed max → returns 0
    @Test
    void computeBudget_blockersExceedMax_returnsZero() {
        completedEntries.add(createEntry(9, 150));
        pinnedEntries.add(createEntry(11, 100));
        int budget = invokeComputeBudget();
        assertThat(budget).isEqualTo(0);
    }

    // Helper to invoke computeBudget via reflection
    private int invokeComputeBudget() {
        try {
            var method = ScheduleService.class.getDeclaredMethod("computeBudget",
                    LocalDate.class, int.class, List.class, List.class, List.class, List.class, Map.class);
            method.setAccessible(true);
            return (int) method.invoke(scheduleService, testDay, maxDailyWorkMinutes,
                    completedEntries, pinnedEntries, userBreaks, userTemplates, exceptionsMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke computeBudget", e);
        }
    }
}
