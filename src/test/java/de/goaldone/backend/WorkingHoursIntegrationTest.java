package de.goaldone.backend;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.repository.WorkingHourEntryRepository;
import de.goaldone.backend.security.GoaldoneUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class WorkingHoursIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkingHourEntryRepository workingHourEntryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        workingHourEntryRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        testUser = User.builder()
                .email("test@user.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        testUser = userRepository.save(testUser);

        authenticateAs(testUser);
    }

    @Test
    void getMyProfileSanityCheck() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@user.com"));
    }

    @Test
    void getWorkingHoursReturns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/users/me/working-hours"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("working-hours-not-found"));
    }

    @Test
    void upsertWorkingHoursSavesCorrectly() throws Exception {
        String json = """
                {
                  "days": [
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "TUESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "WEDNESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "THURSDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "FRIDAY", "isWorkDay": true, "startTime": "09:00", "endTime": "12:00"},
                    {"dayOfWeek": "SATURDAY", "isWorkDay": false},
                    {"dayOfWeek": "SUNDAY", "isWorkDay": false}
                  ]
                }
                """;

        mockMvc.perform(put("/users/me/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].isWorkDay").value(true))
                .andExpect(jsonPath("$.days[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.days[5].dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.days[5].isWorkDay").value(false));

        // Verify with GET
        mockMvc.perform(get("/users/me/working-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7));
    }

    @Test
    void upsertWorkingHoursReturns400OnDuplicateDay() throws Exception {
        String json = """
                {
                  "days": [
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "WEDNESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "THURSDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "FRIDAY", "isWorkDay": true, "startTime": "09:00", "endTime": "12:00"},
                    {"dayOfWeek": "SATURDAY", "isWorkDay": false},
                    {"dayOfWeek": "SUNDAY", "isWorkDay": false}
                  ]
                }
                """;

        mockMvc.perform(put("/users/me/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("working-hours-duplicate-day"));
    }

    @Test
    void upsertWorkingHoursReturns400OnMissingDay() throws Exception {
        String json = """
                {
                  "days": [
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "TUESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "WEDNESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "THURSDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "FRIDAY", "isWorkDay": true, "startTime": "09:00", "endTime": "12:00"},
                    {"dayOfWeek": "SATURDAY", "isWorkDay": false}
                  ]
                }
                """;

        mockMvc.perform(put("/users/me/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("working-hours-must-have-seven-days"));
    }

    @Test
    void upsertWorkingHoursReturns400OnMissingTime() throws Exception {
        String json = """
                {
                  "days": [
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "endTime": "16:00"},
                    {"dayOfWeek": "TUESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "WEDNESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "THURSDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "FRIDAY", "isWorkDay": true, "startTime": "09:00", "endTime": "12:00"},
                    {"dayOfWeek": "SATURDAY", "isWorkDay": false},
                    {"dayOfWeek": "SUNDAY", "isWorkDay": false}
                  ]
                }
                """;

        mockMvc.perform(put("/users/me/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("working-hours-missing-time-for-work-day"));
    }

    @Test
    void upsertWorkingHoursReturns400OnEndBeforeStart() throws Exception {
        String json = """
                {
                  "days": [
                    {"dayOfWeek": "MONDAY", "isWorkDay": true, "startTime": "16:00", "endTime": "08:00"},
                    {"dayOfWeek": "TUESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "WEDNESDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "THURSDAY", "isWorkDay": true, "startTime": "08:00", "endTime": "16:00"},
                    {"dayOfWeek": "FRIDAY", "isWorkDay": true, "startTime": "09:00", "endTime": "12:00"},
                    {"dayOfWeek": "SATURDAY", "isWorkDay": false},
                    {"dayOfWeek": "SUNDAY", "isWorkDay": false}
                  ]
                }
                """;

        mockMvc.perform(put("/users/me/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("working-hours-end-before-start"));
    }
}
