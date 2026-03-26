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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TaskRepository taskRepository;

    private Organization orgA;
    private Organization orgB;
    private User userA;
    private User userB;
    private Task taskA;
    private Task taskB;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = organizationRepository.save(Organization.builder().name("Org A").adminEmail("admin@a.com").build());
        orgB = organizationRepository.save(Organization.builder().name("Org B").adminEmail("admin@b.com").build());

        userA = userRepository.save(User.builder()
                .email("user@a.com")
                .firstName("User")
                .lastName("A")
                .passwordHash("hash")
                .role(Role.USER)
                .organization(orgA)
                .build());

        userB = userRepository.save(User.builder()
                .email("user@b.com")
                .firstName("User")
                .lastName("B")
                .passwordHash("hash")
                .role(Role.USER)
                .organization(orgB)
                .build());

        taskA = taskRepository.save(Task.builder()
                .title("Task A")
                .owner(userA)
                .organization(orgA)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.LOW)
                .estimatedDurationMinutes(30)
                .build());

        taskB = taskRepository.save(Task.builder()
                .title("Task B")
                .owner(userB)
                .organization(orgB)
                .status(TaskStatus.OPEN)
                .cognitiveLoad(CognitiveLoad.LOW)
                .estimatedDurationMinutes(30)
                .build());
    }

    @Test
    void userACannotAccessTaskB() throws Exception {
        authenticateAs(userA);
        mockMvc.perform(get("/tasks/" + taskB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBCannotAccessTaskA() throws Exception {
        authenticateAs(userB);
        mockMvc.perform(get("/tasks/" + taskA.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTasksReturnsOnlyOwnTasks() throws Exception {
        authenticateAs(userA);
        mockMvc.perform(get("/tasks")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Task A"));
    }

    @Test
    void userACannotAccessOrgBDetails() throws Exception {
        authenticateAs(userA);
        // /organizations/me always uses current orgId from JWT, so it should return Org A
        mockMvc.perform(get("/organizations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Org A"));
    }
}
