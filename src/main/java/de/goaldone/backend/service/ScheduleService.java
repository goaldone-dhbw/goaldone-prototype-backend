package de.goaldone.backend.service;

import de.goaldone.backend.entity.Break;
import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.ScheduleEntry;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        int maxBlockMinutes = request.getMaxDailyWorkMinutes() != null ? request.getMaxDailyWorkMinutes() : 240;

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

        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) != 13) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid-schedule-window");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getOrganization() == null || !user.getOrganization().getId().equals(orgId)) {
            throw new org.springframework.security.access.AccessDeniedException("User organization mismatch");
        }
        
        Organization organization = user.getOrganization();

        // 2. Load Tasks
        List<Task> allTasks = taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(
                userId, Arrays.asList(TaskStatus.OPEN, TaskStatus.IN_PROGRESS));
        
        // Stable sort: Deadline ASC, CognitiveLoad DESC
        allTasks.sort(Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCognitiveLoad, Comparator.reverseOrder()));

        Map<UUID, Integer> remainingOneTimeMinutes = new HashMap<>();
        List<Task> oneTimeTasks = new ArrayList<>();
        List<Task> recurringTasks = new ArrayList<>();
        
        for (Task t : allTasks) {
            if (t.getRecurrenceType() != null) {
                recurringTasks.add(t);
            } else {
                oneTimeTasks.add(t);
                remainingOneTimeMinutes.put(t.getId(), t.getEstimatedDurationMinutes());
            }
        }

        List<Break> breaks = breakRepository.findByUserId(userId);
        List<ScheduleEntry> existingEntries = scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to);
        Map<LocalDate, List<ScheduleEntry>> fixedEntriesPerDay = existingEntries.stream()
                .filter(e -> e.isCompleted() || e.isPinned())
                .collect(Collectors.groupingBy(ScheduleEntry::getEntryDate));

        List<ScheduleEntry> newEntries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Instant generationTime = Instant.now();

        // 3. Algorithm Calculation
        for (LocalDate currentDay = from; !currentDay.isAfter(to); currentDay = currentDay.plusDays(1)) {
            java.util.Optional<WorkingHoursService.WorkWindow> windowOpt = workingHoursService.getWorkWindow(userId, currentDay);
            if (windowOpt.isEmpty()) continue;
            
            WorkingHoursService.WorkWindow window = windowOpt.get();
            LocalTime currentTime = window.startTime();
            LocalTime dayEndTime = window.endTime();
            
            List<ScheduleEntry> fixedToday = fixedEntriesPerDay.getOrDefault(currentDay, new ArrayList<>());
            List<TimeBlock> blockers = new ArrayList<>();
            for (ScheduleEntry fe : fixedToday) { blockers.add(new TimeBlock(fe.getStartTime(), fe.getEndTime())); }
            
            // Collect breaks for the day
            for (Break b : breaks) {
                if (matchesRecurrence(b.getRecurrenceType(), b.getRecurrenceInterval(), null, b.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), currentDay)) {
                    // Check for collision with fixed entries
                    if (blockers.stream().noneMatch(fe -> fe.start().equals(b.getStartTime()))) {
                        ScheduleEntry breakEntry = ScheduleEntry.builder()
                                .user(user)
                                .organization(organization)
                                .breakEntry(b)
                                .entryDate(currentDay)
                                .startTime(b.getStartTime())
                                .endTime(b.getEndTime())
                                .entryType(ScheduleEntryType.BREAK)
                                .generatedAt(generationTime)
                                .build();
                        newEntries.add(breakEntry);
                        blockers.add(new TimeBlock(b.getStartTime(), b.getEndTime()));
                    }
                }
            }
            blockers.sort(Comparator.comparing(TimeBlock::start));

            // Tasks to schedule today
            List<Task> tasksForToday = new ArrayList<>();
            for (Task rt : recurringTasks) {
                if ((rt.getStartDate() == null || !currentDay.isBefore(rt.getStartDate())) &&
                    matchesRecurrence(rt.getRecurrenceType(), rt.getRecurrenceInterval(), rt.getStartDate(), from, currentDay)) {
                    tasksForToday.add(rt);
                }
            }
            // One-time tasks that have remaining time and are allowed to start
            for (Task t : oneTimeTasks) {
                if (remainingOneTimeMinutes.get(t.getId()) > 0 && (t.getStartDate() == null || !currentDay.isBefore(t.getStartDate()))) {
                    // Create a virtual task with remaining time for the day filler
                    tasksForToday.add(Task.builder()
                            .id(t.getId())
                            .title(t.getTitle())
                            .cognitiveLoad(t.getCognitiveLoad())
                            .estimatedDurationMinutes(remainingOneTimeMinutes.get(t.getId()))
                            .build());
                }
            }
            // Sort today's tasks: HIGH load first
            tasksForToday.sort(Comparator.comparing(Task::getCognitiveLoad, Comparator.reverseOrder()));

            int continuousHighWorkMinutes = 0;
            Map<UUID, Task> taskMap = allTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));

            for (Task t : tasksForToday) {
                int durationRemaining = t.getEstimatedDurationMinutes();
                
                while (durationRemaining > 0 && currentTime.isBefore(dayEndTime)) {
                    // Skip blockers
                    Optional<TimeBlock> blocker = findOverlappingBlocker(blockers, currentTime);
                    if (blocker.isPresent()) {
                        currentTime = blocker.get().end();
                        continuousHighWorkMinutes = 0; // Reset on any blocker (break or fixed entry)
                        continue;
                    }

                    Optional<TimeBlock> nextBlocker = findNextBlocker(blockers, currentTime);
                    LocalTime nextInterrupt = nextBlocker.map(TimeBlock::start).orElse(dayEndTime);
                    int availableMins = (int) Duration.between(currentTime, nextInterrupt).toMinutes();

                    if (availableMins <= 0) {
                        currentTime = nextInterrupt;
                        continue;
                    }

                    // System Break Logic for HIGH tasks
                    if (t.getCognitiveLoad() == CognitiveLoad.HIGH) {
                        if (continuousHighWorkMinutes >= maxBlockMinutes) {
                            // Insert System Break
                            int breakDuration = maxBlockMinutes / 2;
                            LocalTime breakEnd = currentTime.plusMinutes(breakDuration);
                            if (breakEnd.isAfter(dayEndTime)) breakEnd = dayEndTime;

                            // Create a synthetic break entity for the system break
                            Break systemBreak = Break.builder()
                                    .label("System Break")
                                    .user(user)
                                    .startTime(currentTime)
                                    .endTime(breakEnd)
                                    .recurrenceType(de.goaldone.backend.entity.enums.RecurrenceType.DAILY)
                                    .recurrenceInterval(1)
                                    .build();

                            newEntries.add(ScheduleEntry.builder()
                                    .user(user)
                                    .organization(organization)
                                    .breakEntry(systemBreak)
                                    .entryDate(currentDay)
                                    .startTime(currentTime)
                                    .endTime(breakEnd)
                                    .entryType(ScheduleEntryType.BREAK)
                                    .generatedAt(generationTime)
                                    .build());

                            currentTime = breakEnd;
                            continuousHighWorkMinutes = 0;
                            continue;
                        }

                        int allowedInBlock = maxBlockMinutes - continuousHighWorkMinutes;
                        int scheduled = Math.min(Math.min(availableMins, durationRemaining), allowedInBlock);
                        LocalTime slotEnd = currentTime.plusMinutes(scheduled);

                        newEntries.add(ScheduleEntry.builder()
                                .user(user)
                                .organization(organization)
                                .task(taskMap.get(t.getId()))
                                .entryDate(currentDay)
                                .startTime(currentTime)
                                .endTime(slotEnd)
                                .entryType(ScheduleEntryType.TASK)
                                .generatedAt(generationTime)
                                .build());

                        durationRemaining -= scheduled;
                        if (remainingOneTimeMinutes.containsKey(t.getId())) {
                            remainingOneTimeMinutes.put(t.getId(), remainingOneTimeMinutes.get(t.getId()) - scheduled);
                        }
                        continuousHighWorkMinutes += scheduled;
                        currentTime = slotEnd;
                    } else {
                        // LOW cognitive load task
                        int scheduled = Math.min(availableMins, durationRemaining);
                        LocalTime slotEnd = currentTime.plusMinutes(scheduled);

                        newEntries.add(ScheduleEntry.builder()
                                .user(user)
                                .organization(organization)
                                .task(taskMap.get(t.getId()))
                                .entryDate(currentDay)
                                .startTime(currentTime)
                                .endTime(slotEnd)
                                .entryType(ScheduleEntryType.TASK)
                                .generatedAt(generationTime)
                                .build());

                        durationRemaining -= scheduled;
                        if (remainingOneTimeMinutes.containsKey(t.getId())) {
                            remainingOneTimeMinutes.put(t.getId(), remainingOneTimeMinutes.get(t.getId()) - scheduled);
                        }
                        // For simplicity, LOW tasks don't count towards HIGH block but they don't reset it either?
                        // User said: "Hochleistungsaufgaben nur maximal 4h am Stück". 
                        // Let's assume LOW tasks can be interspersed without resetting the HIGH block count.
                        currentTime = slotEnd;
                    }
                }
            }
        }

        // 4. Post-check for deadlines
        for (Task t : oneTimeTasks) {
            if (remainingOneTimeMinutes.get(t.getId()) > 0 && t.getDeadline() != null) {
                if (!t.getDeadline().isAfter(to)) {
                    warnings.add("deadline-at-risk:" + t.getId());
                }
            }
        }

        // 5. Selectively delete and save
        scheduleEntryRepository.deleteByUserIdAndEntryDateBetweenAndIsCompletedFalseAndIsPinnedFalse(userId, from, to);
        scheduleEntryRepository.flush();
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
                .warnings(new ArrayList<>(new LinkedHashSet<>(warnings)))
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
