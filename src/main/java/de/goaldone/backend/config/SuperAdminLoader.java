package de.goaldone.backend.config;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminLoader implements ApplicationRunner {

    private final SuperAdminProperties superAdminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (superAdminProperties.getSuperAdmins() == null || superAdminProperties.getSuperAdmins().isEmpty()) {
            log.warn("No super admins configured in super-admins.yml — the platform will have no super admin account");
            return;
        }

        for (SuperAdminProperties.SuperAdminEntry entry : superAdminProperties.getSuperAdmins()) {
            if (userRepository.existsByEmail(entry.email())) {
                log.info("Super admin already exists, skipping: {}", entry.email());
            } else {
                User user = new User();
                user.setEmail(entry.email());
                user.setFirstName(entry.firstName());
                user.setLastName(entry.lastName());
                user.setPasswordHash(passwordEncoder.encode(entry.password()));
                user.setRole(Role.SUPER_ADMIN);
                user.setCreatedAt(Instant.now());
                user.setOrganization(null);
                
                userRepository.save(user);
                log.info("Super admin created: {}", entry.email());
            }
        }
    }
}
