package com.nexadesk.notification;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.common.web.SortWhitelist;
import com.nexadesk.notification.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Centre de notifications (temps réel : STOMP /ws, file /user/queue/notifications)")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Mes notifications (les plus récentes d'abord)")
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        // Fixed order (most recent first): no client-chosen sort.
        return service.list(unreadOnly, SortWhitelist.check(pageable, Set.of()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues", description = "Pour le badge au chargement de la page.")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public NotificationDto markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead());
    }
}
