package de.goaldone.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Slf4j
public class StartupValidator implements ApplicationRunner {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Goaldone Startup Validation ===");
        validateJwtSecret();
        validateDatabaseConnection();
        logActiveProfile();
        log.info("=== Startup validation passed ===");
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "STARTUP FAILED: app.jwt.secret is not configured. " +
                "Set the JWT_SECRET environment variable.");
        }
        if (jwtSecret.contains("dev-secret") && activeProfile.contains("prod")) {
            throw new IllegalStateException(
                "STARTUP FAILED: app.jwt.secret contains the dev placeholder value. " +
                "Set a strong JWT_SECRET in production.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "STARTUP FAILED: app.jwt.secret is too short. " +
                "Use at least 32 characters (256 bits) for HS256.");
        }
        log.info("✓ JWT secret configured ({} chars)", jwtSecret.length());
    }

    private void validateDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            log.info("✓ Database connection OK ({})", conn.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            throw new IllegalStateException(
                "STARTUP FAILED: Cannot connect to database. " + e.getMessage(), e);
        }
    }

    private void logActiveProfile() {
        log.info("✓ Active profile: {}", activeProfile);
        if (activeProfile.contains("prod")) {
            log.info("  Running in PRODUCTION mode");
        } else {
            log.warn("  Running in DEVELOPMENT mode — not suitable for production");
        }
    }
}
