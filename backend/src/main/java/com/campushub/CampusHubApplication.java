package com.campushub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Excludes Spring's generated in-memory user. Authentication is the bearer-token chain in
 * identity.security (ADR-003), so no generated password is ever created or logged.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class CampusHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusHubApplication.class, args);
    }
}
