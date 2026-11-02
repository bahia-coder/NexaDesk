package com.nexadesk.notification.dto;

import com.nexadesk.notification.NotificationType;
import java.time.Instant;

public record NotificationDto(Long id, NotificationType type, String title, String message, Long ticketId,
                              String link, boolean read, Instant createdAt) {
}
