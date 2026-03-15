package de.goaldone.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "")
@Data
public class SuperAdminProperties {
    private List<SuperAdminEntry> superAdmins;

    public record SuperAdminEntry(
            String email,
            String firstName,
            String lastName,
            String password
    ) {}
}
