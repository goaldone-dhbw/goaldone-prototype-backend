package de.goaldone.backend;

import de.goaldone.backend.config.SuperAdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SuperAdminProperties.class)
public class GoaldonePrototypeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoaldonePrototypeBackendApplication.class, args);
    }

}
