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
    private final ValidationService validationService;

    private static final LocalTime DAY_START = LocalTime.of(9, 0);

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(UUID userId, LocalDate from, LocalDate to) {
        List<ScheduleEntry> entries = scheduleEntryRepository.findByUserIdAndEntryDateBetween(userId, from, to);
        return mapToScheduleResponse(from, to, entries);
    }

    @Transactional
    public ScheduleResponse generateSchedule(UUID userId, UUID orgId, GenerateScheduleRequest request) {
        validationService.requireNotNull(request.getFrom(), "from");
        validationService.requireNotNull(request.getTo(), "to");
        validationService.requireNotBefore(request.getTo(), request.getFrom(), "to");
        if (request.getMaxDailyWorkMinutes() != null) {
            validationService.requireRange(request.getMaxDailyWorkMinutes(), "maxDailyWorkMinutes", 30, 480);
        }

        LocalDate from = request.getFrom();
        LocalDate to = request.getTo();
        int maxDailyWorkMinutes = request.getMaxDailyWorkMinutes() != null ? request.getMaxDailyWorkMinutes() : 240;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getOrganization() == null || !user.getOrganization().getId().equals(orgId)) {
            throw new org.springframework.security.access.AccessDeniedException("User organization mismatch");
        }
        
        Organization organization = user.getOrganization();

        // 1. Delete existing entries
        scheduleEntryRepository.deleteByUserIdAndEntryDateBetween(userId, from, to);

        // 2. Load tasks
        List<Task> tasks = taskRepository.findByOwnerIdAndStatusInOrderByDeadlineAscCognitiveLoadDesc(
                userId, Arrays.asList(TaskStatus.OPEN, TaskStatus.IN_PROGRESS));
        
        // Robust sort to handle Enum string ordering issues and null deadlines
        tasks.sort(Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCognitiveLoad, Comparator.reverseOrder()));
        
        Deque<Task> taskPool = new ArrayDeque<>(tasks);

        // 3. Load breaks
        List<Break> breaks = breakRepository.findByUserId(userId);
        breaks.sort(Comparator.comparing(Break::getStartTime));

        Instant generationTime = Instant.now();

        // 4. Iterate over days
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            // Only working days (Mon-Fri)
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            // a. Prepare day capacity
            int remainingCapacity = maxDailyWorkMinutes;
            List<Task> tasksForToday = new ArrayList<>();

            while (remainingCapacity > 0 && !taskPool.isEmpty()) {
                Task task = taskPool.pollFirst();
                int duration = task.getEstimatedDurationMinutes();

                if (duration <= remainingCapacity) {
                    tasksForToday.add(task);
                    remainingCapacity -= duration;
                    // Mark as in progress if it wasn't
                    if (task.getStatus() == TaskStatus.OPEN) {
                        task.setStatus(TaskStatus.IN_PROGRESS);
                        taskRepository.save(task);
                    }
                } else {
                    // Split task
                    int scheduledMinutes = remainingCapacity;
                    int leftoverMinutes = duration - scheduledMinutes;

                    // Update original task
                    task.setEstimatedDurationMinutes(scheduledMinutes);
                    task.setStatus(TaskStatus.IN_PROGRESS);
                    taskRepository.save(task);

                    // Create child task
                    Task childTask = Task.builder()
                            .title(task.getTitle() + " (Rest)")
                            .description(task.getDescription())
                            .status(TaskStatus.OPEN)
                            .cognitiveLoad(task.getCognitiveLoad())
                            .estimatedDurationMinutes(leftoverMinutes)
                            .deadline(task.getDeadline())
                            .owner(task.getOwner())
                            .organization(task.getOrganization())
                            .parentTask(task)
                            .build();
                    taskRepository.save(childTask);

                    tasksForToday.add(task);
                    taskPool.addFirst(childTask); // Put rest for next day
                    remainingCapacity = 0;
                }
            }

            // b. Schedule breaks (blockers)
            List<ScheduleEntry> dayEntries = new ArrayList<>();
            for (Break b : breaks) {
                dayEntries.add(ScheduleEntry.builder()
                        .user(user)
                        .organization(organization)
                        .breakEntry(b)
                        .entryDate(date)
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .entryType(ScheduleEntryType.BREAK)
                        .generatedAt(generationTime)
                        .build());
            }

            // c. Re-sort today's tasks by HIGH cognitive load first
            tasksForToday.sort(Comparator.comparing(Task::getCognitiveLoad).reversed());

            // d. Allocate slots
            LocalTime currentTime = DAY_START;
            for (Task t : tasksForToday) {
                int durationRemaining = t.getEstimatedDurationMinutes();
                while (durationRemaining > 0) {
                    // Find next break
                    LocalTime nextStartTime = currentTime;
                    Optional<Break> nextBreak = findNextBreak(breaks, nextStartTime);

                    if (nextBreak.isPresent()) {
                        Break b = nextBreak.get();
                        if (b.getStartTime().isBefore(nextStartTime.plusMinutes(durationRemaining))) {
                            // Interrupted by break
                            if (b.getStartTime().isAfter(nextStartTime)) {
                                // Time before break
                                int minsBefore = (int) Duration.between(nextStartTime, b.getStartTime()).toMinutes();
                                dayEntries.add(createTaskEntry(user, organization, t, date, nextStartTime, b.getStartTime(), generationTime));
                                durationRemaining -= minsBefore;
                            }
                            // Move past break
                            nextStartTime = b.getEndTime();
                            currentTime = nextStartTime;
                        } else {
                            // No interruption before task ends
                            dayEntries.add(createTaskEntry(user, organization, t, date, nextStartTime, nextStartTime.plusMinutes(durationRemaining), generationTime));
                            currentTime = nextStartTime.plusMinutes(durationRemaining);
                            durationRemaining = 0;
                        }
                    } else {
                        // No more breaks today
                        dayEntries.add(createTaskEntry(user, organization, t, date, nextStartTime, nextStartTime.plusMinutes(durationRemaining), generationTime));
                        currentTime = nextStartTime.plusMinutes(durationRemaining);
                        durationRemaining = 0;
                    }
                }
            }
            
            scheduleEntryRepository.saveAll(dayEntries);
        }

        return getSchedule(userId, from, to);
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

    private ScheduleResponse mapToScheduleResponse(LocalDate from, LocalDate to, List<ScheduleEntry> entries) {
        Instant generatedAt = entries.isEmpty() ? Instant.now() : entries.get(0).getGeneratedAt();
        
        int totalWorkMinutes = entries.stream()
                .filter(e -> e.getEntryType() == ScheduleEntryType.TASK)
                .mapToInt(e -> (int) Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
                .sum();

        return ScheduleResponse.builder()
                .generatedAt(OffsetDateTime.ofInstant(generatedAt, ZoneOffset.UTC))
                .from(from)
                .to(to)
                .totalWorkMinutes(totalWorkMinutes)
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
