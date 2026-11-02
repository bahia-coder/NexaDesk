package com.nexadesk.demo;

import com.nexadesk.notification.Notification;
import com.nexadesk.notification.NotificationComposer;
import com.nexadesk.notification.NotificationComposer.Draft;
import com.nexadesk.notification.NotificationRepository;
import com.nexadesk.ticket.PendingReason;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketEvent;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: fills the notification centres by replaying the key events of the last week's tickets
 * through the real {@link NotificationComposer} rules. Notifications older than a day are marked as read.
 * Live SLA alerts are left to the SLA monitor.
 */
@Slf4j
@Component
@Profile("demo")
@Order(5)
@RequiredArgsConstructor
public class DemoNotificationSeeder implements ApplicationRunner {

    private final NotificationRepository notificationRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (notificationRepository.count() > 0) {
            log.info("Notifications already present, skipping demo notifications");
            return;
        }
        Instant now = clock.instant();
        List<Long> admins = userRepository.findByRole_CodeAndActiveTrue(RoleCode.ADMIN).stream().map(User::getId).toList();
        int created = 0;
        for (Ticket ticket : ticketRepository.findByCreatedAtAfterOrderByCreatedAtAsc(now.minus(Duration.ofDays(7)))) {
            List<Long> specialists = userRepository.findActiveTechniciansWithSpecialty(ticket.getCategory().getId())
                    .stream().map(User::getId).toList();
            User requester = ticket.getRequester();
            User assignee = ticket.getAssignee();

            created += replay(TicketEvent.created(ticket, requester.getId()), requester, ticket, admins, specialists,
                    ticket.getCreatedAt(), now);
            if (assignee != null) {
                created += replay(TicketEvent.assigned(ticket, assignee.getId(), null), assignee, ticket, admins,
                        specialists, ticket.getCreatedAt().plus(Duration.ofMinutes(15)), now);
            }
            if (ticket.getStatus() == TicketStatus.RESOLVED) {
                created += replay(TicketEvent.statusChanged(ticket, assignee.getId(), TicketStatus.IN_PROGRESS,
                        TicketStatus.RESOLVED), assignee, ticket, admins, specialists, ticket.getResolvedAt(), now);
            }
            if (ticket.getStatus() == TicketStatus.PENDING && ticket.getPendingReason() == PendingReason.WAITING_USER) {
                created += replay(TicketEvent.statusChanged(ticket, assignee.getId(), TicketStatus.IN_PROGRESS,
                        TicketStatus.PENDING), assignee, ticket, admins, specialists, ticket.getUpdatedAt(), now);
            }
        }
        log.info("Demo notifications created: {}", created);
    }

    private int replay(TicketEvent event, User actor, Ticket ticket, List<Long> admins, List<Long> specialists,
                       Instant at, Instant now) {
        User assignee = ticket.getAssignee();
        NotificationComposer.Context context = new NotificationComposer.Context(event, ticket.getReference(),
                ticket.getTitle(), ticket.getPriority().getLabel(), ticket.getRequester().getId(),
                assignee == null ? null : assignee.getId(), actor.getFullName(),
                assignee == null ? null : assignee.getFullName(), admins, specialists);
        List<Draft> drafts = NotificationComposer.compose(context);
        for (Draft draft : drafts) {
            Notification notification = Notification.builder()
                    .recipient(userRepository.getReferenceById(draft.recipientId()))
                    .type(draft.type())
                    .title(draft.title())
                    .message(draft.message())
                    .ticket(ticket)
                    .link("/tickets/" + ticket.getId())
                    .createdAt(at)
                    .build();
            if (at.isBefore(now.minus(Duration.ofDays(1)))) {
                notification.markRead(at.plus(Duration.ofHours(1)));
            }
            notificationRepository.save(notification);
        }
        return drafts.size();
    }
}
