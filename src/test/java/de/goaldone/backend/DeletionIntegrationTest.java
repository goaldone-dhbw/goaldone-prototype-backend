package de.goaldone.backend;

import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.Task;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.CognitiveLoad;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.entity.enums.TaskStatus;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserRepository;
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

import jakarta.persistence.EntityManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeletionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private Organization testOrg;
    private User superAdmin;
    private User lastAdmin;
    private User secondAdmin;
    private User normalUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        testOrg = Organization.builder()
                .name("Test Org")
                .adminEmail("admin@test.com")
                .build();
        testOrg = organizationRepository.save(testOrg);

        superAdmin = User.builder()
                .email("super@admin.com")
                .firstName("Super")
                .lastName("Admin")
                .passwordHash("hash")
                .role(Role.SUPER_ADMIN)
                .build();
        superAdmin = userRepository.save(superAdmin);

        lastAdmin = User.builder()
                .email("last@admin.com")
                .firstName("Last")
                .lastName("Admin")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .organization(testOrg)
                .build();
        lastAdmin = userRepository.save(lastAdmin);

        secondAdmin = User.builder()
                .email("second@admin.com")
                .firstName("Second")
                .lastName("Admin")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .organization(testOrg)
                .build();
        secondAdmin = userRepository.save(secondAdmin);

        normalUser = User.builder()
                .email("user@test.com")
                .firstName("Normal")
                .lastName("User")
                .passwordHash("hash")
                .role(Role.USER)
                .organization(testOrg)
                .build();
        normalUser = userRepository.save(normalUser);
    }

    @Test
    void superAdminCannotDeleteSelfViaUsersMe() throws Exception {
        authenticateAs(superAdmin);
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastAdminCannotDeleteSelfViaUsersMe() throws Exception {
        userRepository.delete(secondAdmin); // Now lastAdmin is indeed the last one
        authenticateAs(lastAdmin);
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotRemoveLastAdminFromOrg() throws Exception {
        userRepository.delete(secondAdmin); // lastAdmin is now the only admin

        // Remove via members endpoint as the last remaining admin -> must be blocked.
        authenticateAs(lastAdmin);
        mockMvc.perform(delete("/organizations/me/members/" + lastAdmin.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void superAdminCannotCreateTasks() throws Exception {
        authenticateAs(superAdmin);
        String taskJson = """
                {
                    "title": "Super Admin Task",
                    "cognitiveLoad": "LOW",
                    "estimatedDurationMinutes": 60
                }
                """;
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCannotDeleteSelfViaAdminEndpoint() throws Exception {
        authenticateAs(superAdmin);
        mockMvc.perform(delete("/admin/super-admins/" + superAdmin.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOrganizationCascadesToUsers() throws Exception {
        authenticateAs(superAdmin);

        // Ensure some data exists
        Task task = Task.builder()
                .title("Org Task")
                .owner(normalUser)
                .organization(testOrg)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.LOW)
                .estimatedDurationMinutes(30)
                .build();
        taskRepository.save(task);

        // Clear persistence context to prevent Hibernate from validating stale managed users/tasks against the deleted organization 
        // during flush. The delete relies on DB-level ON DELETE CASCADE.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/admin/organizations/" + testOrg.getId()))
                .andExpect(status().isNoContent());

        // Clear persistence context again so repository lookups hit DB state after FK cascades.
        entityManager.flush();
        entityManager.clear();

        // Verify users are gone
        assertFalse(userRepository.findById(lastAdmin.getId()).isPresent());
        assertFalse(userRepository.findById(secondAdmin.getId()).isPresent());
        assertFalse(userRepository.findById(normalUser.getId()).isPresent());
        // Verify tasks are gone (cascaded via Org or User delete)
        assertFalse(taskRepository.findById(task.getId()).isPresent());
    }
}
