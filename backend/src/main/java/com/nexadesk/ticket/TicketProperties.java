package com.nexadesk.ticket;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param reopenWindow how long after closure the requester may still reopen a ticket (an admin always can)
 */
@Validated
@ConfigurationProperties("nexadesk.tickets")
public record TicketProperties(@NotNull Duration reopenWindow, @Min(1) int maxAttachmentsPerTicket) {
}
