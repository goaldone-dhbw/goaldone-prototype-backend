package de.goaldone.backend;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.WorkingHourEntry;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.RecurrenceType;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FlexibleTaskReproductionTest extends BaseIntegrationTest {

    @Autowired
    private RecurringTemplateRepository templateRepository;

    @Autowired
    private WorkingHourEntryRepository workingHourEntryRepository;

    private void createWorkingHours(User user) {
        for (DayOfWeek dow : DayOfWeek.values()) {
            boolean isWorkDay = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            WorkingHourEntry entry = WorkingHourEntry.builder()
                    .user(user)
                    .dayOfWeek(dow)
                    .workDay(isWorkDay)
                    .startTime(isWorkDay ? LocalTime.of(8, 0) : null)
                    .endTime(isWorkDay ? LocalTime.of(17, 0) : null)
                    .build();
            workingHourEntryRepository.save(entry);
        }
    }

    @Test
    public void reproduce_FlexibleTaskDisplacesFixedTask() throws Exception {
        User user = createUser("flexible@example.com");
        authenticateAs(user);
        createWorkingHours(user);

        // Monday, 2026-03-30
        LocalDate monday = LocalDate.of(2026, 3, 30);

        // Template A: Flexible (No start time), 2 hours -> Defaults to 08:00
        templateRepository.save(RecurringTemplate.builder()
                .title("Flexible Task A")
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .durationMinutes(120)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .preferredStartTime(null) // FLEXIBLE
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build());

        // Template B: Fixed (09:00), 15 mins
        templateRepository.save(RecurringTemplate.builder()
                .title("Fixed Task B")
                .cognitiveLoad(CognitiveLoad.LOW)
                .durationMinutes(15)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .preferredStartTime(LocalTime.of(9, 0)) // FIXED
                .owner(user)
                .organizationId(user.getOrganization().getId())
                .build());

        // CURRENT BEHAVIOR: A (08:00-10:00), B (10:00-10:15) -> B is displaced!
        // DESIRED BEHAVIOR (Option 1): B (09:00-09:15), A shifts to 09:15-11:15 (or later) 
        // because 08:00-09:00 is only 1 hour and doesn't fit A.
        mockMvc.perform(get("/schedule?from=" + monday + "&to=" + monday)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[?(@.taskTitle=='Fixed Task B')].startTime").value("09:00"))
            .andExpect(jsonPath("$.entries[?(@.taskTitle=='Flexible Task A')].startTime").value("09:15"));
    }
}
