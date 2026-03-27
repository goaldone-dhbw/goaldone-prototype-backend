package de.goaldone.backend;

import de.goaldone.backend.entity.RecurringTemplate;
import de.goaldone.backend.entity.RecurringException;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.RecurringTemplateRepository;
import de.goaldone.backend.repository.RecurringExceptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecurringTemplateIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RecurringTemplateRepository templateRepository;

    @Autowired
    private RecurringExceptionRepository exceptionRepository;

    @Test
    void createTemplate_shouldReturn201WithFullResponse() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Daily Review");
        request.setDescription(JsonNullable.of("Daily project review"));
        request.setCognitiveLoad(CognitiveLoad.MEDIUM);
        request.setDurationMinutes(45);

        RecurrenceRule ruleReq = new RecurrenceRule();
        ruleReq.setType(RecurrenceType.DAILY);
        ruleReq.setInterval(1);
        request.setRecurrenceRule(ruleReq);
        request.setPreferredStartTime(JsonNullable.of("17:00"));

        MvcResult result = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.title").value("Daily Review"))
            .andExpect(jsonPath("$.cognitiveLoad").value("MEDIUM"))
            .andExpect(jsonPath("$.durationMinutes").value(45))
            .andExpect(jsonPath("$.recurrenceRule.type").value("DAILY"))
            .andReturn();
    }

    @Test
    void listTemplates_shouldReturnPaginatedList() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/recurring-templates?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getTemplate_shouldReturnFullTemplate() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Weekly Standup");
        request.setCognitiveLoad(CognitiveLoad.LOW);
        request.setDurationMinutes(30);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.WEEKLY);
        rule.setInterval(1);
        request.setRecurrenceRule(rule);

        MvcResult createResult = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        RecurringTemplateResponse created = objectMapper.readValue(responseBody, RecurringTemplateResponse.class);

        mockMvc.perform(get("/recurring-templates/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Weekly Standup"))
            .andExpect(jsonPath("$.durationMinutes").value(30));
    }

    @Test
    void updateTemplate_shouldUpdateFields() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        // Create
        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Original Title");
        request.setCognitiveLoad(CognitiveLoad.LOW);
        request.setDurationMinutes(30);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrenceRule(rule);

        MvcResult createResult = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        RecurringTemplateResponse created = objectMapper.readValue(responseBody, RecurringTemplateResponse.class);

        // Update
        UpdateRecurringTemplateRequest updateReq = new UpdateRecurringTemplateRequest();
        updateReq.setTitle("Updated Title");
        updateReq.setDurationMinutes(60);

        mockMvc.perform(patch("/recurring-templates/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"))
            .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    @Test
    void deleteTemplate_shouldReturn204() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("To Delete");
        request.setCognitiveLoad(CognitiveLoad.LOW);
        request.setDurationMinutes(15);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrenceRule(rule);

        MvcResult createResult = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        RecurringTemplateResponse created = objectMapper.readValue(responseBody, RecurringTemplateResponse.class);

        mockMvc.perform(delete("/recurring-templates/" + created.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    void createException_shouldMarkOccurrenceAsCompleted() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Daily Task");
        request.setCognitiveLoad(CognitiveLoad.MEDIUM);
        request.setDurationMinutes(30);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrenceRule(rule);

        MvcResult createResult = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        RecurringTemplateResponse created = objectMapper.readValue(responseBody, RecurringTemplateResponse.class);

        RecurringExceptionRequest exceptionReq = new RecurringExceptionRequest();
        exceptionReq.setType(RecurringExceptionType.COMPLETED);
        LocalDate occurrenceDate = LocalDate.now();

        mockMvc.perform(post("/schedule/recurring/" + created.getId() + "/exceptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exceptionReq)))
            .andExpect(status().isCreated());
    }

    @Test
    void deleteException_shouldRemoveException() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest();
        request.setTitle("Daily Task");
        request.setCognitiveLoad(CognitiveLoad.MEDIUM);
        request.setDurationMinutes(30);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setType(RecurrenceType.DAILY);
        rule.setInterval(1);
        request.setRecurrenceRule(rule);

        MvcResult createResult = mockMvc.perform(post("/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        RecurringTemplateResponse created = objectMapper.readValue(responseBody, RecurringTemplateResponse.class);

        LocalDate occurrenceDate = LocalDate.now();

        mockMvc.perform(delete("/schedule/recurring/" + created.getId() + "/exceptions/" + occurrenceDate))
            .andExpect(status().isNoContent());
    }
}
