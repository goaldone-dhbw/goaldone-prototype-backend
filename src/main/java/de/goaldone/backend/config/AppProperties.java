package de.goaldone.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String frontendUrl;

    private final Mail mail = new Mail();
    private final Jwt jwt = new Jwt();

    @Data
    public static class Mail {
        @NotBlank
        private String from;

        // These can be derived from frontendUrl in the service,
        // but we can also keep them configurable if we want full flexibility.
        private String invitationPath = "/invitations";
        private String passwordResetPath = "/auth/reset-password";
    }

    @Data
    public static class Jwt {
        @NotBlank
        private String secret;
        private long accessTokenExpiry;
        private long refreshTokenExpiry;
        private boolean cookieSecure = true;
        private String cookieSameSite = "Strict";
    }
}
