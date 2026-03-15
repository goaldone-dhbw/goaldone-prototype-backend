package de.goaldone.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "")
@Data
public class SuperAdminProperties {
    @Valid
    private List<SuperAdminEntry> superAdmins;

    public record SuperAdminEntry(
            @NotBlank String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String password
    ) {}
}
