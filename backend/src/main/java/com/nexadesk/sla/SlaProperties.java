package com.nexadesk.sla;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param autoCloseAfter business time after which an unanswered RESOLVED ticket is closed automatically
 * @param monitorInterval delay between two runs of the SLA jobs
 */
@Validated
@ConfigurationProperties("nexadesk.sla")
public record SlaProperties(@NotNull Duration autoCloseAfter, @NotNull Duration monitorInterval) {
}
