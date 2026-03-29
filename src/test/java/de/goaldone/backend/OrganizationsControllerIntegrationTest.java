package de.goaldone.backend;

import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganizationsControllerIntegrationTest extends BaseIntegrationTest {

    private User createAdmin(String email) {
        Organization org = new Organization();
        org.setName("Admin Org " + java.util.UUID.randomUUID());
        org.setAdminEmail(email);
        org = organizationRepository.save(org);

        User user = User.builder()
                .email(email)
                .firstName("Admin")
                .lastName("User")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .organization(org)
                .build();
        return userRepository.save(user);
    }

    @Test
    void getMyOrganization_returns200() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/organizations/me")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void listMembers_asAdmin_returns200() throws Exception {
        User admin = createAdmin("admin@example.com");
        authenticateAs(admin);

        mockMvc.perform(get("/organizations/me/members?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listInvitations_asAdmin_returns200() throws Exception {
        User admin = createAdmin("admin2@example.com");
        authenticateAs(admin);

        mockMvc.perform(get("/organizations/me/invitations?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createInvitation_asAdmin_returns201() throws Exception {
        User admin = createAdmin("admin3@example.com");
        authenticateAs(admin);

        String body = """
            {"email": "invitee@example.com"}
            """;

        mockMvc.perform(post("/organizations/me/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("invitee@example.com"));
    }

    @Test
    void revokeInvitation_asAdmin_returns204() throws Exception {
        User admin = createAdmin("admin4@example.com");
        authenticateAs(admin);

        // Create invitation first
        String createBody = """
            {"email": "revoke@example.com"}
            """;

        String response = mockMvc.perform(post("/organizations/me/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String invitationId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(delete("/organizations/me/invitations/" + invitationId))
            .andExpect(status().isNoContent());
    }

    @Test
    void removeMember_asAdmin_returns204() throws Exception {
        User admin = createAdmin("admin5@example.com");
        authenticateAs(admin);

        // Add another user to the same org
        User member = User.builder()
                .email("member@example.com")
                .firstName("Member")
                .lastName("User")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .organization(admin.getOrganization())
                .build();
        member = userRepository.save(member);

        mockMvc.perform(delete("/organizations/me/members/" + member.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    void updateMemberRole_asAdmin_returns200() throws Exception {
        User admin = createAdmin("admin6@example.com");
        authenticateAs(admin);

        // Add another user
        User member = User.builder()
                .email("rolechange@example.com")
                .firstName("Role")
                .lastName("Change")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .organization(admin.getOrganization())
                .build();
        member = userRepository.save(member);

        String body = """
            {"role": "ADMIN"}
            """;

        mockMvc.perform(patch("/organizations/me/members/" + member.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateOrganizationSettings_asAdmin_returns200() throws Exception {
        User admin = createAdmin("admin7@example.com");
        authenticateAs(admin);

        String body = """
            {"name": "Updated Org Name"}
            """;

        mockMvc.perform(put("/organizations/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Org Name"));
    }

    @Test
    void listMembers_asUser_returns403() throws Exception {
        User user = createUser("regularuser@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/organizations/me/members?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
