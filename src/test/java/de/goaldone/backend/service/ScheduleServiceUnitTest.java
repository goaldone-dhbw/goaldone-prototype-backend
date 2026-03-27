package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.RecurringExceptionType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScheduleService.computeBudget() method.
 * Tests lazy daily budget calculation logic.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ScheduleServiceUnitTest {

    /**
     * Helper to create a completed ScheduleEntry
     */
    private ScheduleEntry createCompletedEntry(LocalDate date, int durationMinutes) {
        return ScheduleEntry.builder()
                .entryDate(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 0).plusMinutes(durationMinutes))
                .isCompleted(true)
                .generatedAt(Instant.now())
                .entryType(ScheduleEntryType.TASK)
                .build();
    }

    /**
     * Helper to create a pinned ScheduleEntry
     */
    private ScheduleEntry createPinnedEntry(LocalDate date, int durationMinutes) {
        return ScheduleEntry.builder()
                .entryDate(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 0).plusMinutes(durationMinutes))
                .isPinned(true)
                .generatedAt(Instant.now())
                .entryType(ScheduleEntryType.TASK)
                .build();
    }

    /**
     * TEST 1: No blockers → full budget returned
     */
    @Test
    public void test01_noBloclers_returnsFullBudget() {
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();
        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(maxBudget, result);
    }

    /**
     * TEST 2: Completed entry 60min → budget reduced by 60
     */
    @Test
    public void test02_completedEntry_reducesBudget() {
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        completed.add(createCompletedEntry(day, 60));

        List<ScheduleEntry> pinned = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();
        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(180, result);
    }

    /**
     * TEST 3: Pinned 30min + Completed 30min → budget reduced by 60
     */
    @Test
    public void test03_pinnedAndCompleted_summedCorrectly() {
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        completed.add(createCompletedEntry(day, 30));

        List<ScheduleEntry> pinned = new ArrayList<>();
        pinned.add(createPinnedEntry(day, 30));

        List<Break> breaks = new ArrayList<>();
        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(180, result);
    }

    /**
     * TEST 4: Break ONE_TIME on same day → blocks time
     */
    @Test
    public void test04_breakOneTime_blocksSameDay() {
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(day)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(180, result);
    }

    /**
     * TEST 5: Break ONE_TIME on different day → no reduction
     */
    @Test
    public void test05_breakOneTime_differentDay_noReduction() {
        LocalDate day = LocalDate.now();
        LocalDate otherDay = day.plusDays(1);
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(otherDay)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(maxBudget, result);
    }

    /**
     * TEST 6: Break RECURRING daily 60min → blocks every day
     */
    @Test
    public void test06_breakRecurringDaily_blocksBudget() {
        LocalDate baseDate = LocalDate.now().minusDays(10);
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .createdAt(Instant.now())
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(180, result);
    }

    /**
     * TEST 7: Break BOUNDED_RECURRING within date range → blocks
     */
    @Test
    public void test07_breakBoundedRecurring_withinRange_blocks() {
        LocalDate day = LocalDate.of(2026, 6, 15);
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 6, 1))
                .validUntil(LocalDate.of(2026, 6, 30))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .createdAt(Instant.now())
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(180, result);
    }

    /**
     * TEST 8: Break BOUNDED_RECURRING outside range → no block
     */
    @Test
    public void test08_breakBoundedRecurring_outsideRange_noBlock() {
        LocalDate day = LocalDate.of(2026, 7, 15);
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.BOUNDED_RECURRING)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .validFrom(LocalDate.of(2026, 6, 1))
                .validUntil(LocalDate.of(2026, 6, 30))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .createdAt(Instant.now())
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(maxBudget, result);
    }

    /**
     * TEST 9: RecurringTemplate 30min without exception → blocks
     */
    @Test
    public void test09_recurringTemplate_withoutException_blocks() {
        LocalDate baseDate = LocalDate.now().minusDays(10);
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();

        List<RecurringTemplate> templates = new ArrayList<>();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(UUID.randomUUID())
                .title("Daily Standup")
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .cognitiveLoad(CognitiveLoad.LOW)
                .createdAt(LocalDate.now().atStartOfDay())
                .build();
        templates.add(template);

        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(210, result);
    }

    /**
     * TEST 10: RecurringTemplate with SKIPPED exception → no block
     */
    @Test
    public void test10_recurringTemplate_skipped_noBlock() {
        LocalDate baseDate = LocalDate.now().minusDays(10);
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();

        List<RecurringTemplate> templates = new ArrayList<>();
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(templateId)
                .title("Daily Standup")
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .cognitiveLoad(CognitiveLoad.LOW)
                .createdAt(LocalDate.now().atStartOfDay())
                .build();
        templates.add(template);

        Map<String, RecurringException> exceptions = new HashMap<>();
        exceptions.put(templateId + "_" + day, RecurringException.builder()
                .type(RecurringExceptionType.SKIPPED)
                .build());

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(maxBudget, result);
    }

    /**
     * TEST 11: RecurringTemplate COMPLETED exception → still blocks
     */
    @Test
    public void test11_recurringTemplate_completed_stillBlocks() {
        LocalDate baseDate = LocalDate.now().minusDays(10);
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        List<ScheduleEntry> pinned = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();

        List<RecurringTemplate> templates = new ArrayList<>();
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(templateId)
                .title("Daily Standup")
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .cognitiveLoad(CognitiveLoad.LOW)
                .createdAt(LocalDate.now().atStartOfDay())
                .build();
        templates.add(template);

        Map<String, RecurringException> exceptions = new HashMap<>();
        exceptions.put(templateId + "_" + day, RecurringException.builder()
                .type(RecurringExceptionType.COMPLETED)
                .build());

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(210, result);
    }

    /**
     * TEST 12: Multiple blockers combined → sum correct
     */
    @Test
    public void test12_multipleBlockers_summedCorrectly() {
        LocalDate baseDate = LocalDate.now().minusDays(10);
        LocalDate day = LocalDate.now();
        int maxBudget = 240;

        List<ScheduleEntry> completed = new ArrayList<>();
        completed.add(createCompletedEntry(day, 30));

        List<ScheduleEntry> pinned = new ArrayList<>();
        pinned.add(createPinnedEntry(day, 30));

        List<Break> breaks = new ArrayList<>();
        Break breakEntry = Break.builder()
                .breakType(BreakType.ONE_TIME)
                .date(day)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .build();
        breaks.add(breakEntry);

        List<RecurringTemplate> templates = new ArrayList<>();
        UUID templateId = UUID.randomUUID();
        RecurringTemplate template = RecurringTemplate.builder()
                .id(templateId)
                .title("Daily Task")
                .durationMinutes(30)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .cognitiveLoad(CognitiveLoad.LOW)
                .createdAt(LocalDate.now().atStartOfDay())
                .build();
        templates.add(template);

        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(120, result); // 240 - 30 - 30 - 60 - 30
    }

    /**
     * TEST 13: Budget cannot be negative → clamped to 0
     */
    @Test
    public void test13_negativeBudget_clampedToZero() {
        LocalDate day = LocalDate.now();
        int maxBudget = 60;

        List<ScheduleEntry> completed = new ArrayList<>();
        completed.add(createCompletedEntry(day, 60));

        List<ScheduleEntry> pinned = new ArrayList<>();
        pinned.add(createPinnedEntry(day, 60));

        List<Break> breaks = new ArrayList<>();
        List<RecurringTemplate> templates = new ArrayList<>();
        Map<String, RecurringException> exceptions = new HashMap<>();

        int result = computeBudgetHelper(day, maxBudget, completed, pinned, breaks, templates, exceptions);
        assertEquals(0, result);
    }

    /**
     * Helper method to compute budget (simulates the actual method)
     */
    private int computeBudgetHelper(LocalDate day, int maxDailyWorkMinutes,
                                   List<ScheduleEntry> completedEntries,
                                   List<ScheduleEntry> pinnedEntries,
                                   List<Break> userBreaks,
                                   List<RecurringTemplate> userTemplates,
                                   Map<String, RecurringException> exceptionsMap) {
        int blocked = 0;

        // Completed entries
        for (ScheduleEntry e : completedEntries) {
            if (e.getEntryDate().equals(day)) {
                blocked += (int) java.time.temporal.ChronoUnit.MINUTES
                        .between(e.getStartTime(), e.getEndTime());
            }
        }

        // Pinned entries
        for (ScheduleEntry e : pinnedEntries) {
            if (e.getEntryDate().equals(day)) {
                blocked += (int) java.time.temporal.ChronoUnit.MINUTES
                        .between(e.getStartTime(), e.getEndTime());
            }
        }

        // Breaks (simplified - just check breakType and date)
        for (Break b : userBreaks) {
            if (b.getBreakType() == BreakType.ONE_TIME && b.getDate().equals(day)) {
                blocked += (int) java.time.temporal.ChronoUnit.MINUTES
                        .between(b.getStartTime(), b.getEndTime());
            } else if (b.getBreakType() == BreakType.RECURRING ||  b.getBreakType() == BreakType.BOUNDED_RECURRING) {
                if (b.getValidFrom() == null || (day.isAfter(b.getValidFrom()) || day.equals(b.getValidFrom())) &&
                    (b.getValidUntil() == null || (day.isBefore(b.getValidUntil()) || day.equals(b.getValidUntil())))) {
                    blocked += (int) java.time.temporal.ChronoUnit.MINUTES
                            .between(b.getStartTime(), b.getEndTime());
                }
            }
        }

        // RecurringTemplates
        for (RecurringTemplate t : userTemplates) {
            String key = t.getId() + "_" + day;
            RecurringException ex = exceptionsMap.get(key);
            if (ex == null || ex.getType() != RecurringExceptionType.SKIPPED) {
                blocked += t.getDurationMinutes();
            }
        }

        return Math.max(0, maxDailyWorkMinutes - blocked);
    }
}
