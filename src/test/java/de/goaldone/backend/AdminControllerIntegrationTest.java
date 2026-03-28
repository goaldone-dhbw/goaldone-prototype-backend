package de.goaldone.backend;

import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerIntegrationTest extends BaseIntegrationTest {

    private User createSuperAdmin(String email) {
        Organization org = new Organization();
        org.setName("SuperAdmin Org " + java.util.UUID.randomUUID());
        org.setAdminEmail(email);
        org = organizationRepository.save(org);

        User user = User.builder()
                .email(email)
                .firstName("Super")
                .lastName("Admin")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.SUPER_ADMIN)
                .organization(org)
                .build();
        return userRepository.save(user);
    }

    @Test
    void listOrganizations_asSuperAdmin_returns200() throws Exception {
        User superAdmin = createSuperAdmin("super@example.com");
        authenticateAs(superAdmin);

        mockMvc.perform(get("/admin/organizations?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createOrganization_asSuperAdmin_returns201() throws Exception {
        User superAdmin = createSuperAdmin("super2@example.com");
        authenticateAs(superAdmin);

        String body = """
            {"name": "New Org", "adminEmail": "orgadmin@example.com"}
            """;

        mockMvc.perform(post("/admin/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Org"));
    }

    @Test
    void deleteOrganization_asSuperAdmin_returns204() throws Exception {
        User superAdmin = createSuperAdmin("super3@example.com");
        authenticateAs(superAdmin);

        // Create org to delete
        String createBody = """
            {"name": "Org To Delete", "adminEmail": "todelete@example.com"}
            """;

        MvcResult createResult = mockMvc.perform(post("/admin/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

        String orgId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/admin/organizations/" + orgId))
            .andExpect(status().isNoContent());
    }

    @Test
    void listSuperAdmins_asSuperAdmin_returns200() throws Exception {
        User superAdmin = createSuperAdmin("super4@example.com");
        authenticateAs(superAdmin);

        mockMvc.perform(get("/admin/super-admins?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void addSuperAdmin_asSuperAdmin_returns201() throws Exception {
        User superAdmin = createSuperAdmin("super5@example.com");
        authenticateAs(superAdmin);

        String body = """
            {"email": "newsuper@example.com"}
            """;

        mockMvc.perform(post("/admin/super-admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    @Test
    void deleteSuperAdmin_asSuperAdmin_returns204() throws Exception {
        User superAdmin = createSuperAdmin("super6@example.com");
        authenticateAs(superAdmin);

        // Create another super admin to delete
        User otherSuperAdmin = createSuperAdmin("other-super@example.com");

        mockMvc.perform(delete("/admin/super-admins/" + otherSuperAdmin.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    void listOrganizations_asUser_returns403() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        mockMvc.perform(get("/admin/organizations?page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
