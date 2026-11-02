package com.nexadesk.notification;

import com.nexadesk.notification.NotificationComposer.Draft;
import com.nexadesk.notification.dto.NotificationDto;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketEvent;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns ticket events into notifications.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: nobody is notified about a change that was finally rolled back.</li>
 *   <li>{@code REQUIRES_NEW}: the original transaction is over, notifications get their own.</li>
 *   <li>Pushed over WebSocket only once stored: a client receiving one can always reload it.</li>
 *   <li>Failures are logged, never propagated: the user's action has already succeeded.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    public static final String USER_DESTINATION = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationMapper mapper;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(TicketEvent event) {
        try {
            dispatch(event);
        } catch (RuntimeException e) {
            log.error("Could not dispatch notifications for {}", event, e);
        }
    }

    private void dispatch(TicketEvent event) {
        Ticket ticket = ticketRepository.findWithDetailsById(event.ticketId()).orElse(null);
        if (ticket == null) {
            return;
        }
        List<Draft> drafts = NotificationComposer.compose(context(event, ticket));
        if (drafts.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        List<Notification> saved = new ArrayList<>();
        for (Draft draft : drafts) {
            saved.add(notificationRepository.save(Notification.builder()
                    .recipient(userRepository.getReferenceById(draft.recipientId()))
                    .type(draft.type())
                    .title(draft.title())
                    .message(draft.message())
                    .ticket(ticket)
                    .link("/tickets/" + ticket.getId())
                    .createdAt(now)
                    .build()));
        }
        pushAfterCommit(saved.stream().map(n -> new Push(n.getRecipient().getId(), mapper.toDto(n))).toList());
    }

    private NotificationComposer.Context context(TicketEvent event, Ticket ticket) {
        String actorName = event.actorId() == null ? "Le système"
                : userRepository.findById(event.actorId()).map(User::getFullName).orElse("Un utilisateur");
        boolean needsSpecialists = event.kind() == TicketEvent.Kind.CREATED
                || (event.kind() == TicketEvent.Kind.STATUS_CHANGED && event.to() == TicketStatus.NEW);
        boolean needsAdmins = event.kind() == TicketEvent.Kind.CREATED || event.kind() == TicketEvent.Kind.SLA_BREACHED
                || (event.kind() == TicketEvent.Kind.SLA_WARNING && ticket.getAssignee() == null);
        List<Long> specialists = needsSpecialists
                ? userRepository.findActiveTechniciansWithSpecialty(ticket.getCategory().getId()).stream()
                        .map(User::getId).toList()
                : List.of();
        List<Long> admins = needsAdmins
                ? userRepository.findByRole_CodeAndActiveTrue(RoleCode.ADMIN).stream().map(User::getId).toList()
                : List.of();
        User assignee = ticket.getAssignee();
        return new NotificationComposer.Context(event, ticket.getReference(), ticket.getTitle(),
                ticket.getPriority().getLabel(), ticket.getRequester().getId(),
                assignee == null ? null : assignee.getId(), actorName,
                assignee == null ? null : assignee.getFullName(), admins, specialists);
    }

    private record Push(Long recipientId, NotificationDto payload) {
    }

    /** The principal name of a WebSocket session is the user id (JWT subject). */
    private void pushAfterCommit(List<Push> pushes) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Push push : pushes) {
                    try {
                        messaging.convertAndSendToUser(String.valueOf(push.recipientId()), USER_DESTINATION,
                                push.payload());
                    } catch (RuntimeException e) {
                        log.warn("WebSocket push failed for user {}", push.recipientId(), e);
                    }
                }
            }
        });
    }
}
