package de.goaldone.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest extends BaseIntegrationTest {

    @Test
    void publicEndpointsAreAccessible() throws Exception {
        // Auth endpoints (assuming non-existing token/login still returns 401/404 but is NOT blocked by filter)
        // Login should be 415 or 400 if body is missing, but NOT 401 if it's permitAll
        String loginJson = """
                {
                    "email": "wrong@test.com",
                    "password": "wrong"
                }
                """;
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/invitations/some-token"))
                .andExpect(status().isNotFound()); // Invitation not found, but reached service
    }

    @Test
    void swaggerIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection()); // Redirects to swagger-ui/index.html
    }

    @Test
    void protectedEndpointsReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized());
        
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/organizations/me"))
                .andExpect(status().isUnauthorized());
    }
}
