package de.goaldone.backend;

import de.goaldone.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsersControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void getMyProfile_returns200() throws Exception {
        User user = createUser("me@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/users/me")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void updateMyProfile_returns200() throws Exception {
        User user = createUser("update@example.com");
        authenticateAs(user);

        String body = """
            {"firstName": "Updated", "lastName": "Name"}
            """;

        mockMvc.perform(put("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated"))
            .andExpect(jsonPath("$.lastName").value("Name"));
    }

    @Test
    void deleteMyAccount_returns204() throws Exception {
        User user = createUser("delete@example.com");
        authenticateAs(user);

        mockMvc.perform(delete("/users/me"))
            .andExpect(status().isNoContent());
    }
}
