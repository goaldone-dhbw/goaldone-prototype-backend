package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.ScheduleEntryType;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.WorkingHoursResponse;
import de.goaldone.backend.repository.BreakRepository;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final TaskRepository taskRepository;
    private final BreakRepository breakRepository;
    private final UserRepository userRepository;
    private final ValidationService validationService;
    private final WorkingHoursService workingHoursService;

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(UUID userId, LocalDate from, LocalDate to) {
        List<ScheduleEntry> entries = scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to);
        return mapToScheduleResponse(from, to, entries, new ArrayList<>());
    }

    @Transactional
    public ScheduleResponse generateSchedule(UUID userId, UUID orgId, GenerateScheduleRequest request) {
        LocalDate from = request.getFrom();
        LocalDate to = request.getTo();
        int maxDailyWorkMinutes = request.getMaxDailyWorkMinutes() != null ? request.getMaxDailyWorkMinutes() : 240;

        // 1. Pre-flight Check
        WorkingHoursResponse workingHours;
        try {
            workingHours = workingHoursService.getWorkingHours(userId);
        } catch (ResourceNotFoundException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "working-hours-missing");
        }

        if (workingHours == null || workingHours.getDays() == null || workingHours.getDays().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "working-hours-missing");
        }

        // 2. Window validation (14 days = from + 13)
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) != 13) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid-schedule-window");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getOrganization() == null || !user.getOrganization().getId().equals(orgId)) {
            throw new org.springframework.security.access.AccessDeniedException("User organization mismatch");
        }
        
        Organization organization = user.getOrganization();

        // 3. Algorithm Calculation in memory
        List<ScheduleEntry> newEntries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Load tasks
        List<Task> allTasks = taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(
                userId, Arrays.asList(TaskStatus.OPEN, TaskStatus.IN_PROGRESS));
        
        // Stable sort: Deadline ASC, CognitiveLoad DESC
        allTasks.sort(Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCognitiveLoad, Comparator.reverseOrder()));

        // Track remaining minutes for one-time tasks across the 14 days
        Map<UUID, Integer> remainingOneTimeMinutes = new HashMap<>();
        List<Task> oneTimeTasks = new ArrayList<>();
        List<Task> recurringTasks = new ArrayList<>();
        
        for (Task t : allTasks) {
            if (t.getRecurrenceType() != null) {
                recurringTasks.add(t);
                if (t.getEstimatedDurationMinutes() > maxDailyWorkMinutes) {
                    warnings.add("unschedulable-task:" + t.getId());
                }
            } else {
                oneTimeTasks.add(t);
                remainingOneTimeMinutes.put(t.getId(), t.getEstimatedDurationMinutes());
                if (t.getEstimatedDurationMinutes() > maxDailyWorkMinutes) {
                    warnings.add("unschedulable-task:" + t.getId());
                }
            }
        }

        // Load breaks
        List<Break> breaks = breakRepository.findByUserId(userId);

        // Load existing fixed/completed entries to block budget
        List<ScheduleEntry> existingEntries = scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to);
        Map<LocalDate, List<ScheduleEntry>> fixedEntriesPerDay = existingEntries.stream()
                .filter(e -> e.isCompleted() || e.isPinned())
                .collect(Collectors.groupingBy(ScheduleEntry::getEntryDate));

        Instant generationTime = Instant.now();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate currentDay = date;
            
            // a. Work Day Check
            java.util.Optional<WorkingHoursService.WorkWindow> windowOpt = workingHoursService.getWorkWindow(userId, currentDay);
            if (windowOpt.isEmpty()) {
                continue;
            }
            WorkingHoursService.WorkWindow window = windowOpt.get();

            // b. Budget Deduction
            List<ScheduleEntry> fixedToday = fixedEntriesPerDay.getOrDefault(currentDay, new ArrayList<>());
            int fixedMinutesUsed = fixedToday.stream()
                    .mapToInt(e -> (int) Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
                    .sum();
            
            int availableBudget = maxDailyWorkMinutes - fixedMinutesUsed;
            if (availableBudget <= 0) {
                continue;
            }

            List<ScheduleEntry> dayEntries = new ArrayList<>();
            List<Task> tasksForToday = new ArrayList<>();

            // c. Block breaks
            for (Break b : breaks) {
                if (matchesRecurrence(b.getRecurrenceType(), b.getRecurrenceInterval(), null, b.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), currentDay)) {
                    dayEntries.add(ScheduleEntry.builder()
                            .user(user)
                            .organization(organization)
                            .breakEntry(b)
                            .entryDate(currentDay)
                            .startTime(b.getStartTime())
                            .endTime(b.getEndTime())
                            .entryType(ScheduleEntryType.BREAK)
                            .generatedAt(generationTime)
                            .build());
                }
            }

            // d. Recurrence Expansion (Tasks)
            for (Task rt : recurringTasks) {
                if (rt.getStartDate() != null && currentDay.isBefore(rt.getStartDate())) {
                    continue;
                }
                if (matchesRecurrence(rt.getRecurrenceType(), rt.getRecurrenceInterval(), rt.getStartDate(), from, currentDay)) {
                    if (rt.getEstimatedDurationMinutes() <= availableBudget) {
                        tasksForToday.add(rt);
                        availableBudget -= rt.getEstimatedDurationMinutes();
                    } else if (availableBudget > 0) {
                        // If it doesn't fit fully, we still add it but warn? 
                        // The prompt says: "Fill remaining time with tasks up to maxDailyWorkMinutes"
                        // For recurring tasks, if it doesn't fit in budget, we might skip or partial.
                        // Let's stick to simple: if it fits partially, we schedule what we can.
                        int duration = Math.min(rt.getEstimatedDurationMinutes(), availableBudget);
                        tasksForToday.add(Task.builder()
                                .id(rt.getId())
                                .title(rt.getTitle())
                                .cognitiveLoad(rt.getCognitiveLoad())
                                .estimatedDurationMinutes(duration)
                                .build());
                        availableBudget -= duration;
                        warnings.add("task-budget-exceeded:" + rt.getId());
                    }
                }
            }

            // e. Fill remaining budget with One-Time Tasks
            for (Task t : oneTimeTasks) {
                if (availableBudget <= 0) break;
                if (t.getStartDate() != null && currentDay.isBefore(t.getStartDate())) continue;
                
                int remaining = remainingOneTimeMinutes.get(t.getId());
                if (remaining <= 0) continue;

                int durationToSchedule = Math.min(remaining, availableBudget);
                
                tasksForToday.add(Task.builder()
                        .id(t.getId())
                        .title(t.getTitle())
                        .cognitiveLoad(t.getCognitiveLoad())
                        .estimatedDurationMinutes(durationToSchedule)
                        .build());
                
                if (durationToSchedule < remaining) {
                    warnings.add("task-budget-exceeded:" + t.getId());
                }
                
                remainingOneTimeMinutes.put(t.getId(), remaining - durationToSchedule);
                availableBudget -= durationToSchedule;
            }

            // f. Sort today's tasks: HIGH cognitive load first
            tasksForToday.sort(Comparator.comparing(Task::getCognitiveLoad, Comparator.reverseOrder()));

            // g. Slot Allocation
            LocalTime currentTime = window.startTime();
            LocalTime dayEndTime = window.endTime();

            List<TimeBlock> blockers = new ArrayList<>();
            for (ScheduleEntry be : dayEntries) { blockers.add(new TimeBlock(be.getStartTime(), be.getEndTime())); }
            for (ScheduleEntry fe : fixedToday) { blockers.add(new TimeBlock(fe.getStartTime(), fe.getEndTime())); }
            blockers.sort(Comparator.comparing(TimeBlock::start));

            Map<UUID, Task> taskMap = allTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));

            for (Task t : tasksForToday) {
                int durationRemaining = t.getEstimatedDurationMinutes();
                while (durationRemaining > 0 && !currentTime.isAfter(dayEndTime)) {
                    LocalTime slotStart = currentTime;
                    
                    Optional<TimeBlock> blocker = findOverlappingBlocker(blockers, slotStart);
                    if (blocker.isPresent()) {
                        currentTime = blocker.get().end();
                        continue;
                    }

                    Optional<TimeBlock> nextBlocker = findNextBlocker(blockers, slotStart);
                    LocalTime nextInterrupt = nextBlocker.map(TimeBlock::start).orElse(dayEndTime);
                    
                    int availableMins = (int) Duration.between(slotStart, nextInterrupt).toMinutes();
                    if (availableMins > 0) {
                        int scheduled = Math.min(availableMins, durationRemaining);
                        LocalTime slotEnd = slotStart.plusMinutes(scheduled);
                        
                        newEntries.add(ScheduleEntry.builder()
                                .user(user)
                                .organization(organization)
                                .task(taskMap.get(t.getId()))
                                .entryDate(currentDay)
                                .startTime(slotStart)
                                .endTime(slotEnd)
                                .entryType(ScheduleEntryType.TASK)
                                .generatedAt(generationTime)
                                .build());
                        
                        durationRemaining -= scheduled;
                        currentTime = slotEnd;
                    } else {
                        currentTime = nextInterrupt;
                    }
                }
            }
            // Add breaks for the day to newEntries as well
            newEntries.addAll(dayEntries);
        }

        // 4. Post-check for deadlines
        for (Task t : oneTimeTasks) {
            if (remainingOneTimeMinutes.get(t.getId()) > 0 && t.getDeadline() != null) {
                if (!t.getDeadline().isAfter(to)) {
                    warnings.add("deadline-at-risk:" + t.getId());
                }
            }
        }

        // 5. Selective delete
        scheduleEntryRepository.deleteByUserIdAndEntryDateBetweenAndIsCompletedFalseAndIsPinnedFalse(userId, from, to);

        // 6. Persist
        scheduleEntryRepository.saveAll(newEntries);

        return mapToScheduleResponse(from, to, scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to), warnings);
    }

    private boolean matchesRecurrence(de.goaldone.backend.entity.enums.RecurrenceType type, Integer interval, LocalDate startDate, LocalDate createdAt, LocalDate currentDay) {
        if (type == null) return false;
        if (interval == null || interval < 1) interval = 1;
        
        LocalDate baseDate = startDate != null ? startDate : createdAt;
        if (currentDay.isBefore(baseDate)) return false;

        switch (type) {
            case DAILY:
                return java.time.temporal.ChronoUnit.DAYS.between(baseDate, currentDay) % interval == 0;
            case WEEKLY:
                return baseDate.getDayOfWeek() == currentDay.getDayOfWeek() && 
                       (java.time.temporal.ChronoUnit.WEEKS.between(baseDate, currentDay) % interval == 0);
            case MONTHLY:
                return baseDate.getDayOfMonth() == currentDay.getDayOfMonth() &&
                       (java.time.temporal.ChronoUnit.MONTHS.between(baseDate, currentDay) % interval == 0);
            default:
                return false;
        }
    }

    private record TimeBlock(LocalTime start, LocalTime end) {}

    private Optional<TimeBlock> findOverlappingBlocker(List<TimeBlock> blockers, LocalTime time) {
        return blockers.stream()
                .filter(b -> !time.isBefore(b.start()) && time.isBefore(b.end()))
                .findFirst();
    }

    private Optional<TimeBlock> findNextBlocker(List<TimeBlock> blockers, LocalTime after) {
        return blockers.stream()
                .filter(b -> b.start().isAfter(after) || b.start().equals(after))
                .findFirst();
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry completeScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (entry.isCompleted()) {
            throw new ConflictException("schedule-entry-already-completed");
        }
        entry.setCompleted(true);
        return mapToScheduleEntryModel(scheduleEntryRepository.save(entry));
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry pinScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (entry.isPinned()) {
            throw new ConflictException("schedule-entry-already-pinned");
        }
        entry.setPinned(true);
        return mapToScheduleEntryModel(scheduleEntryRepository.save(entry));
    }

    @Transactional
    public de.goaldone.backend.model.ScheduleEntry unpinScheduleEntry(UUID entryId, UUID userId) {
        ScheduleEntry entry = scheduleEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found"));
        if (!entry.isPinned()) {
            throw new ConflictException("schedule-entry-not-pinned");
        }
        entry.setPinned(false);
        return mapToScheduleEntryModel(scheduleEntryRepository.save(entry));
    }

    private Optional<Break> findNextBreak(List<Break> breaks, LocalTime after) {
        return breaks.stream()
                .filter(b -> b.getEndTime().isAfter(after))
                .findFirst();
    }

    private ScheduleEntry createTaskEntry(User user, Organization org, Task task, LocalDate date, LocalTime start, LocalTime end, Instant genTime) {
        return ScheduleEntry.builder()
                .user(user)
                .organization(org)
                .task(task)
                .entryDate(date)
                .startTime(start)
                .endTime(end)
                .entryType(ScheduleEntryType.TASK)
                .generatedAt(genTime)
                .build();
    }

    private ScheduleResponse mapToScheduleResponse(LocalDate from, LocalDate to, List<ScheduleEntry> entries, List<String> warnings) {
        Instant generatedAt = entries.stream()
                .map(ScheduleEntry::getGeneratedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Instant.now());
        
        int totalWorkMinutes = entries.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.TASK)
                .mapToInt(e -> (int) Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
                .sum();

        return ScheduleResponse.builder()
                .generatedAt(OffsetDateTime.ofInstant(generatedAt, ZoneOffset.UTC))
                .from(from)
                .to(to)
                .totalWorkMinutes(totalWorkMinutes)
                .warnings(new ArrayList<>(new LinkedHashSet<>(warnings))) // Unique warnings
                .entries(entries.stream().map(this::mapToScheduleEntryModel).toList())
                .build();
    }

    private de.goaldone.backend.model.ScheduleEntry mapToScheduleEntryModel(ScheduleEntry entity) {
        de.goaldone.backend.model.ScheduleEntry model = new de.goaldone.backend.model.ScheduleEntry();
        model.setId(entity.getId());
        model.setDate(entity.getEntryDate());
        model.setStartTime(entity.getStartTime().toString());
        model.setEndTime(entity.getEndTime().toString());
        model.setType(de.goaldone.backend.model.ScheduleEntry.TypeEnum.valueOf(entity.getEntryType().name()));
        model.setIsCompleted(entity.isCompleted());
        model.setIsPinned(entity.isPinned());

        if (entity.getTask() != null) {
            model.setTaskId(org.openapitools.jackson.nullable.JsonNullable.of(entity.getTask().getId()));
            model.setTaskTitle(org.openapitools.jackson.nullable.JsonNullable.of(entity.getTask().getTitle()));
        }

        if (entity.getBreakEntry() != null) {
            model.setBreakId(org.openapitools.jackson.nullable.JsonNullable.of(entity.getBreakEntry().getId()));
            model.setBreakLabel(org.openapitools.jackson.nullable.JsonNullable.of(entity.getBreakEntry().getLabel()));
        }

        return model;
    }
}
