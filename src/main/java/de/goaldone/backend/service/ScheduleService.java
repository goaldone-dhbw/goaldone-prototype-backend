package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.RecurringExceptionType;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.RecurringExceptionRepository;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final TaskRepository taskRepository;
    private final BreakRepository breakRepository;
    private final RecurringTemplateRepository recurringTemplateRepository;
    private final RecurringExceptionRepository recurringExceptionRepository;
    private final UserRepository userRepository;
    private final WorkingHoursService workingHoursService;

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(UUID userId, LocalDate from, LocalDate to) {
        // Load real ScheduleEntries (ONE_TIME)
        List<ScheduleEntry> realEntries = scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to);
        // Map to group entries by date for overlap resolution
        Map<LocalDate, List<de.goaldone.backend.model.ScheduleEntry>> entriesByDate = new HashMap<>();

        // Add real entries to the map
        for (de.goaldone.backend.model.ScheduleEntry entry : realEntries.stream().map(this::toScheduleEntryDto).toList()) {
            entriesByDate.computeIfAbsent(entry.getDate(), k -> new ArrayList<>()).add(entry);
        }

        // Load RecurringTemplates and build virtual entries (RECURRING) on-the-fly
        List<RecurringTemplate> templates = recurringTemplateRepository.findByOwnerIdAndOrganizationId(
                userId, getCurrentOrgId(userId));

        // Load exceptions for the date range
        List<UUID> templateIds = templates.stream().map(RecurringTemplate::getId).collect(Collectors.toList());
        Map<String, RecurringException> exceptionsMap = new HashMap<>();
        if (!templateIds.isEmpty()) {
            recurringExceptionRepository.findByTemplateIdInAndOccurrenceDateBetween(templateIds, from, to)
                    .forEach(ex -> exceptionsMap.put(ex.getTemplate().getId() + "_" + ex.getOccurrenceDate(), ex));
        }

        // Get working hours for slot calculation
        Map<DayOfWeek, WorkingHourEntry> workingHours = loadWorkingHoursMap(userId);

        for (RecurringTemplate template : templates) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                // Skip non-workdays
                Optional<WorkingHourEntry> optWorkDay = getWorkingHourEntry(workingHours, day.getDayOfWeek());
                if (optWorkDay.isEmpty() || !optWorkDay.get().isWorkDay()) {
                    continue;
                }

                if (!recurrenceMatchesDay(template.getRecurrenceType(), template.getRecurrenceInterval(),
                        template.getCreatedAt().toLocalDate(), day)) {
                    continue;
                }

                String key = template.getId() + "_" + day;
                RecurringException ex = exceptionsMap.get(key);

                // Skip if explicitly skipped
                if (ex != null && ex.getType() == RecurringExceptionType.SKIPPED) {
                    continue;
                }

                LocalDate displayDate = day;
                WorkingHourEntry workDay = optWorkDay.get();
                LocalTime displayStart = template.getPreferredStartTime();
                if (displayStart == null) {
                    displayStart = workDay.getStartTime();
                }

                // Handle rescheduled
                if (ex != null && ex.getType() == RecurringExceptionType.RESCHEDULED) {
                    displayDate = ex.getNewDate();
                    if (ex.getNewStartTime() != null) {
                        displayStart = ex.getNewStartTime();
                    }
                }

                de.goaldone.backend.model.ScheduleEntry dto = new de.goaldone.backend.model.ScheduleEntry();
                dto.setSource(de.goaldone.backend.model.ScheduleEntry.SourceEnum.RECURRING);
                dto.setTemplateId(org.openapitools.jackson.nullable.JsonNullable.of(template.getId()));
                dto.setTemplateTitle(org.openapitools.jackson.nullable.JsonNullable.of(template.getTitle()));
                dto.setTaskTitle(org.openapitools.jackson.nullable.JsonNullable.of(template.getTitle())); // Convenience field
                dto.setOccurrenceDate(org.openapitools.jackson.nullable.JsonNullable.of(day));
                dto.setDate(displayDate);
                dto.setStartTime(displayStart.toString());
                dto.setEndTime(displayStart.plusMinutes(template.getDurationMinutes()).toString());
                dto.setType(de.goaldone.backend.model.ScheduleEntry.TypeEnum.TASK);
                dto.setIsCompleted(ex != null && ex.getType() == RecurringExceptionType.COMPLETED);
                dto.setIsPinned(ex != null && ex.getType() == RecurringExceptionType.PINNED);

                entriesByDate.computeIfAbsent(displayDate, k -> new ArrayList<>()).add(dto);
            }
        }

        // Resolve overlaps per day
        List<de.goaldone.backend.model.ScheduleEntry> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<de.goaldone.backend.model.ScheduleEntry>> entry : entriesByDate.entrySet()) {
            LocalDate day = entry.getKey();
            List<de.goaldone.backend.model.ScheduleEntry> dayEntries = entry.getValue();

            // Split into fixed (has preferredStartTime or is ONE_TIME) and flexible (no preferredStartTime)
            // Note: templates with preferredStartTime or rescheduled exceptions are fixed.
            // Templates without preferredStartTime that are NOT rescheduled are flexible.
            List<de.goaldone.backend.model.ScheduleEntry> fixed = new ArrayList<>();
            List<de.goaldone.backend.model.ScheduleEntry> flexible = new ArrayList<>();

            for (de.goaldone.backend.model.ScheduleEntry e : dayEntries) {
                boolean isFlexible = false;
                if (e.getSource() == de.goaldone.backend.model.ScheduleEntry.SourceEnum.RECURRING) {
                    UUID templateId = e.getTemplateId().get();
                    RecurringTemplate t = templates.stream().filter(temp -> temp.getId().equals(templateId)).findFirst().orElse(null);
                    String key = templateId + "_" + e.getOccurrenceDate().get();
                    RecurringException ex = exceptionsMap.get(key);
                    
                    // Flexible if template has no preferred start time AND no rescheduled exception
                    if (t != null && t.getPreferredStartTime() == null) {
                        if (ex == null || ex.getType() != RecurringExceptionType.RESCHEDULED) {
                            isFlexible = true;
                        }
                    }
                }
                
                if (isFlexible) {
                    flexible.add(e);
                } else {
                    fixed.add(e);
                }
            }

            // Phase 1: Place fixed entries and resolve overlaps among them
            fixed.sort(Comparator.comparing(e -> LocalTime.parse(e.getStartTime())));
            List<de.goaldone.backend.model.ScheduleEntry> placed = new ArrayList<>();
            LocalTime currentFreeTime = null;
            for (de.goaldone.backend.model.ScheduleEntry item : fixed) {
                LocalTime itemStart = LocalTime.parse(item.getStartTime());
                LocalTime itemEnd = LocalTime.parse(item.getEndTime());
                int duration = (int) ChronoUnit.MINUTES.between(itemStart, itemEnd);

                if (currentFreeTime != null && itemStart.isBefore(currentFreeTime)) {
                    itemStart = currentFreeTime;
                    itemEnd = itemStart.plusMinutes(duration);
                    item.setStartTime(itemStart.toString());
                    item.setEndTime(itemEnd.toString());
                }
                currentFreeTime = itemEnd;
                placed.add(item);
            }

            // Phase 2: Place flexible entries into gaps
            WorkingHourEntry workDay = getWorkingHourEntry(workingHours, day.getDayOfWeek()).get();
            LocalTime workdayStart = workDay.getStartTime();
            LocalTime workdayEnd = workDay.getEndTime();

            for (de.goaldone.backend.model.ScheduleEntry flex : flexible) {
                int duration = (int) ChronoUnit.MINUTES.between(LocalTime.parse(flex.getStartTime()), LocalTime.parse(flex.getEndTime()));
                
                // Find first gap large enough
                LocalTime searchStart = workdayStart;
                boolean found = false;
                
                while (!found && !searchStart.plusMinutes(duration).isAfter(workdayEnd)) {
                    LocalTime gapEnd = searchStart.plusMinutes(duration);
                    final LocalTime finalSearchStart = searchStart;
                    final LocalTime finalGapEnd = gapEnd;
                    
                    // Check if gap overlaps with any already placed entry
                    boolean overlaps = placed.stream().anyMatch(p -> {
                        LocalTime pStart = LocalTime.parse(p.getStartTime());
                        LocalTime pEnd = LocalTime.parse(p.getEndTime());
                        return finalSearchStart.isBefore(pEnd) && finalGapEnd.isAfter(pStart);
                    });
                    
                    if (!overlaps) {
                        flex.setStartTime(searchStart.toString());
                        flex.setEndTime(gapEnd.toString());
                        placed.add(flex);
                        // Re-sort to keep search logic simple
                        placed.sort(Comparator.comparing(e -> LocalTime.parse(e.getStartTime())));
                        found = true;
                    } else {
                        // Move searchStart to the end of the first overlapping entry to find next gap
                        LocalTime nextPotential = placed.stream()
                            .filter(p -> LocalTime.parse(p.getStartTime()).isBefore(finalGapEnd) && LocalTime.parse(p.getEndTime()).isAfter(finalSearchStart))
                            .map(p -> LocalTime.parse(p.getEndTime()))
                            .max(Comparator.naturalOrder())
                            .orElse(searchStart.plusMinutes(1));
                        searchStart = nextPotential;
                    }
                }
                
                if (!found) {
                    // If no gap found, append to the end and shift (it will probably go beyond workdayEnd)
                    LocalTime lastEnd = placed.isEmpty() ? workdayStart : placed.stream().map(p -> LocalTime.parse(p.getEndTime())).max(Comparator.naturalOrder()).get();
                    flex.setStartTime(lastEnd.toString());
                    flex.setEndTime(lastEnd.plusMinutes(duration).toString());
                    placed.add(flex);
                    placed.sort(Comparator.comparing(e -> LocalTime.parse(e.getStartTime())));
                }
            }
            result.addAll(placed);
        }

        // Final sort for the whole range
        result.sort(Comparator.comparing(de.goaldone.backend.model.ScheduleEntry::getDate)
                .thenComparing(e -> LocalTime.parse(e.getStartTime())));

        ScheduleResponse response = new ScheduleResponse();
        response.setFrom(from);
        response.setTo(to);
        response.setGeneratedAt(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        response.setTotalWorkMinutes(0);
        response.setWarnings(new ArrayList<>());
        response.setEntries(result);

        return response;
    }

    @Transactional
    public ScheduleResponse generateSchedule(UUID userId, UUID orgId, GenerateScheduleRequest request) {
        LocalDate from = request.getFrom();
        int maxDailyWorkMinutes = request.getMaxDailyWorkMinutes() != null ? request.getMaxDailyWorkMinutes() : 240;

        // ══════════════════════════════════════════════════════════
        // 1. Pre-Flight & Validierung
        // ══════════════════════════════════════════════════════════
        WorkingHoursResponse workingHoursResp;
        try {
            workingHoursResp = workingHoursService.getWorkingHours(userId);
        } catch (ResourceNotFoundException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "working-hours-missing");
        }

        if (workingHoursResp == null || workingHoursResp.getDays() == null || workingHoursResp.getDays().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "working-hours-missing");
        }

        Map<DayOfWeek, WorkingHourEntry> workingHours = loadWorkingHoursMap(userId);

        // ══════════════════════════════════════════════════════════
        // 2. Tabula Rasa & Blocker
        // ══════════════════════════════════════════════════════════

        // 2a. Lösche alle ScheduleEntries ab 'from' WHERE isCompleted == false AND isPinned == false
        scheduleEntryRepository.deleteByUserIdAndEntryDateGreaterThanEqualAndIsCompletedFalseAndIsPinnedFalse(userId, from);
        scheduleEntryRepository.flush();

        // 2b. Sammle alle Blocker pro Tag (Completed, Pinned, Breaks, Recurring)
        Map<LocalDate, List<BlockerItem>> dailyBlockers = new HashMap<>();
        LocalDate recurringWindowEnd = from.plusDays(28);

        // Completed Entries als Blocker (Fixed)
        scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsCompletedTrue(userId, from)
                .forEach(e -> dailyBlockers.computeIfAbsent(e.getEntryDate(), k -> new ArrayList<>())
                        .add(new BlockerItem(e.getStartTime(), (int) ChronoUnit.MINUTES.between(e.getStartTime(), e.getEndTime()), false)));

        // Pinned Entries als Blocker (Fixed)
        scheduleEntryRepository.findByUserIdAndEntryDateGreaterThanEqualAndIsPinnedTrue(userId, from)
                .forEach(e -> dailyBlockers.computeIfAbsent(e.getEntryDate(), k -> new ArrayList<>())
                        .add(new BlockerItem(e.getStartTime(), (int) ChronoUnit.MINUTES.between(e.getStartTime(), e.getEndTime()), false)));

        // 2c. Breaks als Blocker (Fixed)
        List<Break> userBreaks = breakRepository.findByUserId(userId);
        for (Break b : userBreaks) {
            int breakDuration = (int) ChronoUnit.MINUTES.between(b.getStartTime(), b.getEndTime());
            for (LocalDate day = from; !day.isAfter(recurringWindowEnd); day = day.plusDays(1)) {
                if (BreakService.breaksBlocksDay(b, day)) {
                    dailyBlockers.computeIfAbsent(day, k -> new ArrayList<>())
                            .add(new BlockerItem(b.getStartTime(), breakDuration, false));
                }
            }
        }

        // 2d. RecurringTemplates als Blocker (28-Tage-Fenster)
        List<RecurringTemplate> userTemplates = recurringTemplateRepository.findByOwnerIdAndOrganizationId(userId, orgId);
        List<UUID> templateIds = userTemplates.stream().map(RecurringTemplate::getId).collect(Collectors.toList());
        final Map<String, RecurringException> exceptionsMap = new HashMap<>();
        if (!templateIds.isEmpty()) {
            recurringExceptionRepository.findByTemplateIdInAndOccurrenceDateBetween(templateIds, from, recurringWindowEnd)
                    .forEach(ex -> exceptionsMap.put(ex.getTemplate().getId() + "_" + ex.getOccurrenceDate(), ex));
        }

        for (RecurringTemplate t : userTemplates) {
            for (LocalDate day = from; !day.isAfter(recurringWindowEnd); day = day.plusDays(1)) {
                // Skip non-workdays
                Optional<WorkingHourEntry> optWorkDay = getWorkingHourEntry(workingHours, day.getDayOfWeek());
                if (optWorkDay.isEmpty() || !optWorkDay.get().isWorkDay()) continue;

                if (!recurrenceMatchesDay(t.getRecurrenceType(), t.getRecurrenceInterval(), t.getCreatedAt().toLocalDate(), day)) continue;
                
                String key = t.getId() + "_" + day;
                RecurringException ex = exceptionsMap.get(key);
                if (ex != null && ex.getType() == RecurringExceptionType.SKIPPED) continue;

                LocalTime start = t.getPreferredStartTime();
                boolean isFlexible = (start == null);
                if (start == null) start = optWorkDay.get().getStartTime();
                
                if (ex != null && ex.getType() == RecurringExceptionType.RESCHEDULED) {
                    if (ex.getNewStartTime() != null) start = ex.getNewStartTime();
                    isFlexible = false; // Rescheduled is fixed
                }
                dailyBlockers.computeIfAbsent(day, k -> new ArrayList<>()).add(new BlockerItem(start, t.getDurationMinutes(), isFlexible));
            }
        }

        // Berechne blockerMinutes Map durch Zwei-Phasen-Einplanung
        Map<LocalDate, Integer> blockerMinutes = new HashMap<>();
        for (Map.Entry<LocalDate, List<BlockerItem>> entry : dailyBlockers.entrySet()) {
            LocalDate day = entry.getKey();
            List<BlockerItem> items = entry.getValue();
            
            List<BlockerItem> fixed = items.stream().filter(i -> !i.isFlexible).collect(Collectors.toList());
            List<BlockerItem> flexible = items.stream().filter(i -> i.isFlexible).collect(Collectors.toList());

            // Phase 1: Fixed
            fixed.sort(Comparator.comparing(i -> i.start));
            List<BlockerItem> placed = new ArrayList<>();
            LocalTime currentFreeTime = null;
            for (BlockerItem item : fixed) {
                LocalTime itemStart = item.start;
                if (currentFreeTime != null && itemStart.isBefore(currentFreeTime)) {
                    itemStart = currentFreeTime;
                }
                LocalTime itemEnd = itemStart.plusMinutes(item.duration);
                placed.add(new BlockerItem(itemStart, item.duration, false));
                currentFreeTime = itemEnd;
            }

            // Phase 2: Flexible
            WorkingHourEntry workDay = getWorkingHourEntry(workingHours, day.getDayOfWeek()).get();
            LocalTime workdayStart = workDay.getStartTime();
            LocalTime workdayEnd = workDay.getEndTime();

            for (BlockerItem flex : flexible) {
                LocalTime searchStart = workdayStart;
                boolean found = false;
                while (!found && !searchStart.plusMinutes(flex.duration).isAfter(workdayEnd)) {
                    LocalTime gapEnd = searchStart.plusMinutes(flex.duration);
                    final LocalTime finalSearchStart = searchStart;
                    final LocalTime finalGapEnd = gapEnd;
                    boolean overlaps = placed.stream().anyMatch(p -> {
                        LocalTime pStart = p.start;
                        LocalTime pEnd = pStart.plusMinutes(p.duration);
                        return finalSearchStart.isBefore(pEnd) && finalGapEnd.isAfter(pStart);
                    });
                    if (!overlaps) {
                        placed.add(new BlockerItem(searchStart, flex.duration, true));
                        placed.sort(Comparator.comparing(i -> i.start));
                        found = true;
                    } else {
                        LocalTime nextPotential = placed.stream()
                            .filter(p -> p.start.isBefore(finalGapEnd) && p.start.plusMinutes(p.duration).isAfter(finalSearchStart))
                            .map(p -> p.start.plusMinutes(p.duration))
                            .max(Comparator.naturalOrder())
                            .orElse(searchStart.plusMinutes(1));
                        searchStart = nextPotential;
                    }
                }
                if (!found) {
                    LocalTime lastEnd = placed.isEmpty() ? workdayStart : placed.stream().map(p -> p.start.plusMinutes(p.duration)).max(Comparator.naturalOrder()).get();
                    placed.add(new BlockerItem(lastEnd, flex.duration, true));
                    placed.sort(Comparator.comparing(i -> i.start));
                }
            }

            // Gesamtdauer berechnen
            int totalDailyBlocker = 0;
            currentFreeTime = null;
            for (BlockerItem item : placed) {
                LocalTime itemStart = item.start;
                if (currentFreeTime != null && itemStart.isBefore(currentFreeTime)) {
                    itemStart = currentFreeTime;
                }
                LocalTime itemEnd = itemStart.plusMinutes(item.duration);
                int actualDuration = (int) ChronoUnit.MINUTES.between(itemStart, itemEnd);
                if (actualDuration > 0) {
                    totalDailyBlocker += actualDuration;
                    currentFreeTime = itemEnd;
                }
            }
            blockerMinutes.put(day, totalDailyBlocker);
        }

        // ══════════════════════════════════════════════════════════
        // 3. Task Pool vorbereiten (nur einmalige Tasks)
        // ══════════════════════════════════════════════════════════
        List<Task> allTasks = taskRepository.findByOwnerIdAndStatusInAndRecurrenceTypeIsNull(userId,
                List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS));

        List<TaskPoolEntry> pool = allTasks.stream()
                .map(t -> new TaskPoolEntry(t, t.getEstimatedDurationMinutes()))
                .collect(Collectors.toCollection(ArrayList::new));

        LocalDate currentDay = from;
        LocalDate lastScheduledDay = null;
        List<ScheduleEntry> result = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ══════════════════════════════════════════════════════════
        // 4. Iteration: Tagesplanung (Dynamic Rolling Window)
        // ══════════════════════════════════════════════════════════
        while (!pool.isEmpty()) {

            // 4a. Entferne Tasks mit Deadline < currentDay
            for (int i = pool.size() - 1; i >= 0; i--) {
                TaskPoolEntry e = pool.get(i);
                if (e.getTask().getDeadline() != null && e.getTask().getDeadline().isBefore(currentDay)) {
                    warnings.add("deadline-missed:" + e.getTask().getId());
                    pool.remove(i);
                }
            }
            if (pool.isEmpty()) break;

            // 4b. Nicht-Arbeitstage überspringen
            Optional<WorkingHourEntry> optWorkDay = getWorkingHourEntry(workingHours, currentDay.getDayOfWeek());
            if (optWorkDay.isEmpty() || !optWorkDay.get().isWorkDay()) {
                currentDay = currentDay.plusDays(1);
                continue;
            }
            WorkingHourEntry workDay = optWorkDay.get();
            final LocalDate dayForFilter = currentDay;

            // 4c. startDate-Filter: Tasks mit startDate > currentDay temporär ausfiltern
            List<TaskPoolEntry> readyTasks = pool.stream()
                    .filter(e -> e.getTask().getStartDate() == null
                            || !e.getTask().getStartDate().isAfter(dayForFilter))
                    .collect(Collectors.toList());

            if (readyTasks.isEmpty()) {
                currentDay = currentDay.plusDays(1);
                continue;
            }

            // 4d. MSTF: Slack berechnen und sortieren
            for (TaskPoolEntry e : readyTasks) {
                if (e.getTask().getDeadline() == null) {
                    e.setSlack(Integer.MAX_VALUE);
                } else {
                    int needed = (int) Math.ceil(
                            (double) e.getRestDurationMinutes() / maxDailyWorkMinutes);
                    e.setSlack(countWorkdaysBetween(currentDay, e.getTask().getDeadline(), workingHours)
                            - needed);
                }
            }

            // Sortierung: 1. Slack aufsteigend (∞ ans Ende), 2. CognitiveLoad HIGH zuerst
            readyTasks.sort(Comparator.comparingInt(TaskPoolEntry::getSlack)
                    .thenComparing((a, b) -> b.getTask().getCognitiveLoad().ordinal()
                            - a.getTask().getCognitiveLoad().ordinal()));

            // 4e. Freies Tagesbudget berechnen
            int blocked = blockerMinutes.getOrDefault(currentDay, 0);
            int budget = Math.max(0, maxDailyWorkMinutes - blocked);

            if (budget <= 0) {
                currentDay = currentDay.plusDays(1);
                continue;
            }

            int currentMinuteOffset = 0;

            // 4f. Inner Loop: Tasks für den Tag einplanen
            for (TaskPoolEntry entry : readyTasks) {
                if (budget <= 0) break;

                if (entry.getRestDurationMinutes() <= budget) {
                    // Task passt komplett
                    result.add(createEntry(entry.getTask(), currentDay,
                            entry.getRestDurationMinutes(), workDay, currentMinuteOffset));
                    currentMinuteOffset += entry.getRestDurationMinutes();
                    budget -= entry.getRestDurationMinutes();
                    lastScheduledDay = currentDay;
                    pool.remove(entry);
                } else {
                    // Splitting nötig
                    result.add(createEntry(entry.getTask(), currentDay,
                            budget, workDay, currentMinuteOffset));
                    entry.setRestDurationMinutes(entry.getRestDurationMinutes() - budget);
                    lastScheduledDay = currentDay;
                    budget = 0;
                }
            }

            currentDay = currentDay.plusDays(1);
        }

        // ══════════════════════════════════════════════════════════
        // 5. Post-Processing & Persistierung
        // ══════════════════════════════════════════════════════════
        scheduleEntryRepository.saveAll(result);

        LocalDate responseTo = lastScheduledDay != null ? lastScheduledDay : from;
        int totalWorkMinutes = result.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.TASK)
                .mapToInt(e -> (int) ChronoUnit.MINUTES.between(e.getStartTime(), e.getEndTime()))
                .sum();

        ScheduleResponse response = new ScheduleResponse();
        response.setFrom(from);
        response.setTo(responseTo);
        response.setGeneratedAt(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        response.setTotalWorkMinutes(totalWorkMinutes);
        response.setWarnings(warnings);
        response.setEntries(result.stream().map(this::toScheduleEntryDto).collect(Collectors.toList()));

        return response;
    }

    /**
     * Create a ScheduleEntry for a task on a given day with a given duration.
     */
    private ScheduleEntry createEntry(Task task, LocalDate day, int durationMinutes, WorkingHourEntry workDay, int minuteOffset) {
        LocalTime startTime = workDay.getStartTime().plusMinutes(minuteOffset);
        LocalTime endTime = startTime.plusMinutes(durationMinutes);

        return ScheduleEntry.builder()
                .user(task.getOwner())
                .organization(task.getOrganization())
                .task(task)
                .entryDate(day)
                .startTime(startTime)
                .endTime(endTime)
                .entryType(ScheduleEntryType.TASK)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Count working days between start (inclusive) and end (inclusive).
     */
    private int countWorkdaysBetween(LocalDate start, LocalDate end, Map<DayOfWeek, WorkingHourEntry> workingHours) {
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Optional<WorkingHourEntry> optEntry = getWorkingHourEntry(workingHours, d.getDayOfWeek());
            if (optEntry.isPresent() && optEntry.get().isWorkDay()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if a recurrence rule matches a given day.
     * MVP-focused: all WEEKLY/MONTHLY Tage that match the day-of-week/day-of-month are included.
     */
    private boolean recurrenceMatchesDay(de.goaldone.backend.entity.enums.RecurrenceType type, Integer interval,
                                        LocalDate baseDate, LocalDate currentDay) {
        if (type == null) return false;
        if (interval == null || interval < 1) interval = 1;

        if (currentDay.isBefore(baseDate)) return false;

        switch (type) {
            case DAILY:
                return ChronoUnit.DAYS.between(baseDate, currentDay) % interval == 0;
            case WEEKLY:
                return baseDate.getDayOfWeek() == currentDay.getDayOfWeek() &&
                        (ChronoUnit.WEEKS.between(baseDate, currentDay) % interval == 0);
            case MONTHLY:
                return baseDate.getDayOfMonth() == currentDay.getDayOfMonth() &&
                        (ChronoUnit.MONTHS.between(baseDate, currentDay) % interval == 0);
            default:
                return false;
        }
    }

    /**
     * Load working hours into a map keyed by DayOfWeek.
     */
    private Map<DayOfWeek, WorkingHourEntry> loadWorkingHoursMap(UUID userId) {
        WorkingHoursResponse resp = workingHoursService.getWorkingHours(userId);
        return resp.getDays().stream()
                .map(dayDto -> {
                    DayOfWeek dow = DayOfWeek.valueOf(dayDto.getDayOfWeek().getValue());
                    WorkingHourEntry entry = new WorkingHourEntry();
                    entry.setDayOfWeek(dow);
                    entry.setWorkDay(dayDto.getIsWorkDay());
                    if (dayDto.getStartTime() != null) {
                        entry.setStartTime(LocalTime.parse(dayDto.getStartTime()));
                    }
                    if (dayDto.getEndTime() != null) {
                        entry.setEndTime(LocalTime.parse(dayDto.getEndTime()));
                    }
                    return entry;
                })
                .collect(Collectors.toMap(WorkingHourEntry::getDayOfWeek, e -> e));
    }

    /**
     * Get working hour entry for a day of week.
     */
    private Optional<WorkingHourEntry> getWorkingHourEntry(Map<DayOfWeek, WorkingHourEntry> map, DayOfWeek dow) {
        return Optional.ofNullable(map.get(dow));
    }

    /**
     * Get current organization ID for a user.
     */
    private UUID getCurrentOrgId(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getOrganization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry completeScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (entry.isCompleted()) {
            throw new ConflictException("schedule-entry-already-completed");
        }
        entry.setCompleted(true);
        return toScheduleEntryDto(scheduleEntryRepository.save(entry));
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry pinScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (entry.isPinned()) {
            throw new ConflictException("schedule-entry-already-pinned");
        }
        entry.setPinned(true);
        return toScheduleEntryDto(scheduleEntryRepository.save(entry));
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry unpinScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (!entry.isPinned()) {
            throw new ConflictException("schedule-entry-not-pinned");
        }
        entry.setPinned(false);
        return toScheduleEntryDto(scheduleEntryRepository.save(entry));
    }

    /**
     * Convert ScheduleEntry entity to DTO (ONE_TIME source).
     */
    private de.goaldone.backend.model.ScheduleEntry toScheduleEntryDto(ScheduleEntry entity) {
        de.goaldone.backend.model.ScheduleEntry dto = new de.goaldone.backend.model.ScheduleEntry();
        dto.setSource(de.goaldone.backend.model.ScheduleEntry.SourceEnum.ONE_TIME);
        dto.setEntryId(org.openapitools.jackson.nullable.JsonNullable.of(entity.getId()));
        dto.setDate(entity.getEntryDate());
        dto.setStartTime(entity.getStartTime().toString());
        dto.setEndTime(entity.getEndTime().toString());
        dto.setType(de.goaldone.backend.model.ScheduleEntry.TypeEnum.valueOf(entity.getEntryType().name()));
        dto.setIsCompleted(entity.isCompleted());
        dto.setIsPinned(entity.isPinned());

        if (entity.getTask() != null) {
            dto.setTaskId(org.openapitools.jackson.nullable.JsonNullable.of(entity.getTask().getId()));
            dto.setTaskTitle(org.openapitools.jackson.nullable.JsonNullable.of(entity.getTask().getTitle()));
        }

        if (entity.getBreakEntry() != null) {
            dto.setBreakId(org.openapitools.jackson.nullable.JsonNullable.of(entity.getBreakEntry().getId()));
            dto.setBreakLabel(org.openapitools.jackson.nullable.JsonNullable.of(entity.getBreakEntry().getLabel()));
        }

        return dto;
    }

    /**
     * Task pool entry for the algorithm.
     */
    private static class TaskPoolEntry {
        private final Task task;
        private int restDurationMinutes;
        private int slack;

        TaskPoolEntry(Task task, int restDurationMinutes) {
            this.task = task;
            this.restDurationMinutes = restDurationMinutes;
        }

        Task getTask() { return task; }
        int getRestDurationMinutes() { return restDurationMinutes; }
        void setRestDurationMinutes(int value) { this.restDurationMinutes = value; }
        int getSlack() { return slack; }
        void setSlack(int value) { this.slack = value; }
    }

    /**
     * Helper class for budget calculation.
     */
    private static class BlockerItem {
        LocalTime start;
        int duration;
        boolean isFlexible;
        BlockerItem(LocalTime start, int duration, boolean isFlexible) {
            this.start = start;
            this.duration = duration;
            this.isFlexible = isFlexible;
        }
    }

    /**
     * Helper class for working hour entries.
     */
    private static class WorkingHourEntry {
        private DayOfWeek dayOfWeek;
        private boolean workDay;
        private LocalTime startTime;
        private LocalTime endTime;

        DayOfWeek getDayOfWeek() { return dayOfWeek; }
        void setDayOfWeek(DayOfWeek value) { this.dayOfWeek = value; }
        boolean isWorkDay() { return workDay; }
        void setWorkDay(boolean value) { this.workDay = value; }
        LocalTime getStartTime() { return startTime; }
        void setStartTime(LocalTime value) { this.startTime = value; }
        LocalTime getEndTime() { return endTime; }
        void setEndTime(LocalTime value) { this.endTime = value; }
    }
}
