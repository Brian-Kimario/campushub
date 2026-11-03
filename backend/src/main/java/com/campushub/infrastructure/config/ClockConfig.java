package com.campushub.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One UTC clock for the application (ADR-013). Tests may substitute a fixed clock. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
