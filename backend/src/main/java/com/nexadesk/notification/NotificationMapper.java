package com.nexadesk.notification;

import com.nexadesk.notification.dto.NotificationDto;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getTicket() == null ? null : n.getTicket().getId(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
