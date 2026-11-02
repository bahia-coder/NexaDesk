package com.nexadesk.notification;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.notification.dto.NotificationDto;
import com.nexadesk.security.CurrentUser;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current user's notification centre. Every query is scoped to the caller. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(boolean unreadOnly, Pageable pageable) {
        Long me = currentUser.id();
        return PageResponse.from((unreadOnly
                ? repository.findByRecipient_IdAndReadAtIsNullOrderByCreatedAtDescIdDesc(me, pageable)
                : repository.findByRecipient_IdOrderByCreatedAtDescIdDesc(me, pageable)).map(mapper::toDto));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByRecipient_IdAndReadAtIsNull(currentUser.id());
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification notification = repository.findByIdAndRecipient_Id(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.markRead(clock.instant());
        return mapper.toDto(notification);
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUser.id(), clock.instant());
    }
}
