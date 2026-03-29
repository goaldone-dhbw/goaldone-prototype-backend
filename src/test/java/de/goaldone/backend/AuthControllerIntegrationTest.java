package de.goaldone.backend;

import de.goaldone.backend.entity.Invitation;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.repository.InvitationRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InvitationRepository invitationRepository;

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        createUser("login@example.com");

        String body = """
            {"email": "login@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.user.email").value("login@example.com"))
            .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void login_badPassword_returns401() throws Exception {
        createUser("bad@example.com");

        String body = """
            {"email": "bad@example.com", "password": "wrongpassword"}
            """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidCookie_returns200() throws Exception {
        createUser("refresh@example.com");

        // Login first to get refresh cookie
        String body = """
            {"email": "refresh@example.com", "password": "password123"}
            """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        // Extract refresh_token cookie
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(post("/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withCookie_returns204() throws Exception {
        createUser("logout@example.com");

        String body = """
            {"email": "logout@example.com", "password": "password123"}
            """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/auth/logout")
                .cookie(refreshCookie))
            .andExpect(status().isNoContent())
            .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void logout_noCookie_returns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_validRequest_returns204() throws Exception {
        User user = createUser("changepw@example.com");
        authenticateAs(user);

        String body = """
            {"currentPassword": "password123", "newPassword": "newPassword456"}
            """;

        mockMvc.perform(post("/auth/password/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNoContent());
    }

    @Test
    void getInvitationInfo_validToken_returns200() throws Exception {
        User inviter = createUser("inviter@example.com");

        Invitation invitation = Invitation.builder()
                .email("new@example.com")
                .token("test-token-123")
                .organization(inviter.getOrganization())
                .invitedBy(inviter)
                .role(Role.USER)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        invitationRepository.save(invitation);

        mockMvc.perform(get("/auth/invitations/test-token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void acceptInvitation_validToken_returns201() throws Exception {
        User inviter = createUser("inviter2@example.com");

        Invitation invitation = Invitation.builder()
                .email("accept@example.com")
                .token("accept-token-456")
                .organization(inviter.getOrganization())
                .invitedBy(inviter)
                .role(Role.USER)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        invitationRepository.save(invitation);

        String body = """
            {"firstName": "New", "lastName": "User", "password": "securePass123"}
            """;

        mockMvc.perform(post("/auth/invitations/accept-token-456/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.user.email").value("accept@example.com"));
    }
}
