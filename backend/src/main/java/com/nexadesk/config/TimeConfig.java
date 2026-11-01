package com.nexadesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Clock} so time-dependent business logic (SLA deadlines, auto-close, token expiry)
 * never calls {@code Instant.now()} directly and can be tested with a fixed clock.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
