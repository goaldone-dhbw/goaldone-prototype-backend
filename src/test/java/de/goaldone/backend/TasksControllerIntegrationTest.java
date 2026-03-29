package de.goaldone.backend;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.CreateTaskRequest;
import de.goaldone.backend.model.UpdateTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TasksControllerIntegrationTest extends BaseIntegrationTest {

    private String createTaskAndGetId(User user) throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Test Task");
        request.setCognitiveLoad(CognitiveLoad.MEDIUM);
        request.setEstimatedDurationMinutes(60);

        MvcResult result = mockMvc.perform(post("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createTask_returns201() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("New Task");
        request.setCognitiveLoad(CognitiveLoad.HIGH);
        request.setEstimatedDurationMinutes(120);

        mockMvc.perform(post("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("New Task"))
            .andExpect(jsonPath("$.cognitiveLoad").value("HIGH"))
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void listTasks_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/tasks?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getTask_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        String taskId = createTaskAndGetId(user);

        mockMvc.perform(get("/tasks/" + taskId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Test Task"));
    }

    @Test
    void updateTask_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        String taskId = createTaskAndGetId(user);

        UpdateTaskRequest updateReq = new UpdateTaskRequest();
        updateReq.setTitle("Updated Title");

        mockMvc.perform(put("/tasks/" + taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void deleteTask_returns204() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        String taskId = createTaskAndGetId(user);

        mockMvc.perform(delete("/tasks/" + taskId))
            .andExpect(status().isNoContent());
    }

    @Test
    void completeTask_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        String taskId = createTaskAndGetId(user);

        mockMvc.perform(patch("/tasks/" + taskId + "/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void reopenTask_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);
        String taskId = createTaskAndGetId(user);

        // Complete first
        mockMvc.perform(patch("/tasks/" + taskId + "/complete"))
            .andExpect(status().isOk());

        // Then reopen
        mockMvc.perform(patch("/tasks/" + taskId + "/reopen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
