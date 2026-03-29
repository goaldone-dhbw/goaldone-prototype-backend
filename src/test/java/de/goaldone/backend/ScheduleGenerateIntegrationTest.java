package de.goaldone.backend;

import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.WorkingHourEntry;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ScheduleGenerateIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkingHourEntryRepository workingHourEntryRepository;

    private void createWorkingHours(User user) {
        for (DayOfWeek dow : DayOfWeek.values()) {
            boolean isWorkDay = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            workingHourEntryRepository.save(WorkingHourEntry.builder()
                    .user(user)
                    .dayOfWeek(dow)
                    .workDay(isWorkDay)
                    .startTime(isWorkDay ? LocalTime.of(8, 0) : null)
                    .endTime(isWorkDay ? LocalTime.of(17, 0) : null)
                    .build());
        }
    }

    @Test
    void generateSchedule_withTasks_returns200() throws Exception {
        User user = createUser("gen@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        taskRepository.save(Task.builder()
                .title("Task 1")
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .estimatedDurationMinutes(60)
                .owner(user)
                .organization(user.getOrganization())
                .build());

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(LocalDate.of(2026, 3, 30)); // A Monday

        mockMvc.perform(post("/schedule/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray())
            .andExpect(jsonPath("$.from").exists())
            .andExpect(jsonPath("$.to").exists());
    }

    @Test
    void generateSchedule_noWorkingHours_returns400() throws Exception {
        User user = createUser("nowhours@example.com");
        authenticateAs(user);

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(LocalDate.of(2026, 3, 30)); // A Monday

        mockMvc.perform(post("/schedule/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void generateSchedule_emptyPool_returnsEmptyEntries() throws Exception {
        User user = createUser("empty@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(LocalDate.of(2026, 3, 30)); // A Monday

        mockMvc.perform(post("/schedule/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isEmpty());
    }
}
