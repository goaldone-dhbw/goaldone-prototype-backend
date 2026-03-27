package de.goaldone.backend;

import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RBACIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization testOrg;
    private User normalUser;
    private User orgAdmin;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        testOrg = Organization.builder()
                .name("Test Org")
                .adminEmail("admin@test.com")
                .build();
        testOrg = organizationRepository.save(testOrg);

        normalUser = User.builder()
                .email("user@test.com")
                .firstName("User")
                .lastName("Normal")
                .passwordHash("hash")
                .role(Role.USER)
                .organization(testOrg)
                .build();
        normalUser = userRepository.save(normalUser);

        orgAdmin = User.builder()
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("Org")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .organization(testOrg)
                .build();
        orgAdmin = userRepository.save(orgAdmin);

        superAdmin = User.builder()
                .email("super@admin.com")
                .firstName("Super")
                .lastName("Admin")
                .passwordHash("hash")
                .role(Role.SUPER_ADMIN)
                .build();
        superAdmin = userRepository.save(superAdmin);
    }

    @Test
    void normalUserCannotUpdateOrganizationSettings() throws Exception {
        authenticateAs(normalUser);
        String updateJson = """
                {
                    "name": "Hacked Name"
                }
                """;
        mockMvc.perform(put("/organizations/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgAdminCanUpdateOrganizationSettings() throws Exception {
        authenticateAs(orgAdmin);
        String updateJson = """
                {
                    "name": "Updated Org Name"
                }
                """;
        mockMvc.perform(put("/organizations/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());
    }

    @Test
    void normalUserCannotAccessAdminEndpoints() throws Exception {
        authenticateAs(normalUser);
        mockMvc.perform(get("/admin/organizations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgAdminCannotAccessAdminEndpoints() throws Exception {
        authenticateAs(orgAdmin);
        mockMvc.perform(get("/admin/organizations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanAccessAdminEndpoints() throws Exception {
        authenticateAs(superAdmin);
        mockMvc.perform(get("/admin/organizations")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }
}
