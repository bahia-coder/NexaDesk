package com.nexadesk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (SLA monitor, auto-close). Can be switched off with nexadesk.scheduling.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nexadesk.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
