package de.goaldone.backend;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.model.BreakType;
import de.goaldone.backend.model.CreateBreakRequest;
import de.goaldone.backend.model.RecurrenceRule;
import de.goaldone.backend.model.RecurrenceType;
import de.goaldone.backend.repository.BreakRepository;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BreakIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BreakRepository breakRepository;

    @Test
    public void createBreak_ONE_TIME_returns201() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("Doctor Appointment");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.now()));

        mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.label").value("Doctor Appointment"))
            .andExpect(jsonPath("$.breakType").value("ONE_TIME"));
    }

    @Test
    public void createBreak_RECURRING_returns201() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("Daily Lunch");
        request.setStartTime("12:00");
        request.setEndTime("13:00");
        request.setBreakType(BreakType.RECURRING);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrence(rule);

        mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.breakType").value("RECURRING"));
    }

    @Test
    public void createBreak_BOUNDED_RECURRING_returns201() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("Summer Break");
        request.setStartTime("14:00");
        request.setEndTime("15:00");
        request.setBreakType(BreakType.BOUNDED_RECURRING);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrence(rule);
        request.setValidFrom(JsonNullable.of(LocalDate.of(2026, 6, 1)));
        request.setValidUntil(JsonNullable.of(LocalDate.of(2026, 8, 31)));

        mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.breakType").value("BOUNDED_RECURRING"));
    }

    @Test
    public void listBreaks_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/breaks")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    public void getBreak_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("Test Break");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.now()));

        String createResponse = mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Extract ID from response (simplified)
        String breakId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/breaks/" + breakId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Test Break"));
    }

    @Test
    public void updateBreak_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("Old Label");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.now()));

        String createResponse = mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String breakId = objectMapper.readTree(createResponse).get("id").asText();

        CreateBreakRequest updateRequest = new CreateBreakRequest();
        updateRequest.setLabel("New Label");
        updateRequest.setStartTime("14:00");
        updateRequest.setEndTime("15:00");
        updateRequest.setBreakType(BreakType.ONE_TIME);
        updateRequest.setDate(JsonNullable.of(LocalDate.now()));

        mockMvc.perform(patch("/breaks/" + breakId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("New Label"));
    }

    @Test
    public void deleteBreak_returns204() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateBreakRequest request = new CreateBreakRequest();
        request.setLabel("To Delete");
        request.setStartTime("10:00");
        request.setEndTime("11:00");
        request.setBreakType(BreakType.ONE_TIME);
        request.setDate(JsonNullable.of(LocalDate.now()));

        String createResponse = mockMvc.perform(post("/breaks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String breakId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(delete("/breaks/" + breakId))
            .andExpect(status().isNoContent());
    }
}
