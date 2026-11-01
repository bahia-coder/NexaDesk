package com.nexadesk.sla;

import com.nexadesk.ticket.SlaState;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketSla;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background SLA work, run every {@code nexadesk.sla.monitor-interval}:
 * <ol>
 *   <li>flag tickets crossing the warning threshold or their deadline — once per threshold;</li>
 *   <li>close RESOLVED tickets left unanswered for {@code nexadesk.sla.auto-close-after} of business time.</li>
 * </ol>
 * Each job is its own {@code @Scheduled @Transactional} method: the scheduler calls it through the Spring proxy,
 * so the transaction applies. (A single scheduled method calling the others on {@code this} would bypass the proxy
 * and run them without a transaction.) Tests call them the same way, through the bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaJobs {

    private static final List<TicketStatus> RUNNING = List.of(TicketStatus.NEW, TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS);

    private final TicketRepository ticketRepository;
    private final TicketSla ticketSla;
    private final TicketHistoryRecorder history;
    private final SlaSettings settings;
    private final SlaProperties properties;
    private final Clock clock;

    /** @return the number of alerts raised */
    @Scheduled(fixedDelayString = "${nexadesk.sla.monitor-interval}", initialDelayString = "PT20S")
    @Transactional
    public int checkDeadlines() {
        Instant now = clock.instant();
        int alerts = 0;
        for (Ticket ticket : ticketRepository.findSlaWatchList(RUNNING)) {
            SlaState state = ticketSla.view(ticket).state();
            if (state == SlaState.BREACHED && ticketRepository.markSlaBreach(ticket.getId(), now) == 1) {
                history.recordInternal(ticket, null, HistoryAction.SLA_BREACHED, "Échéance de résolution dépassée");
                alerts++;
            } else if (state == SlaState.AT_RISK && ticket.getSlaWarningSentAt() == null
                    && ticketRepository.markSlaWarning(ticket.getId(), now) == 1) {
                history.recordInternal(ticket, null, HistoryAction.SLA_WARNING, "Échéance de résolution proche");
                alerts++;
            }
        }
        if (alerts > 0) {
            log.info("SLA monitor raised {} alert(s)", alerts);
        }
        return alerts;
    }

    /** @return the number of tickets closed */
    @Scheduled(fixedDelayString = "${nexadesk.sla.monitor-interval}", initialDelayString = "PT25S")
    @Transactional
    public int autoCloseResolved() {
        Instant now = clock.instant();
        WorkingCalendar calendar = settings.current().calendar();
        // Business time is never shorter than calendar time: tickets resolved more recently cannot qualify.
        Instant candidatesBefore = now.minus(properties.autoCloseAfter());
        int closed = 0;
        for (Ticket ticket : ticketRepository.findByStatusAndResolvedAtBefore(TicketStatus.RESOLVED, candidatesBefore)) {
            Instant deadline = BusinessTime.add(ticket.getResolvedAt(), properties.autoCloseAfter(), calendar, true);
            if (deadline.isBefore(now)) {
                ticket.setStatus(TicketStatus.CLOSED);
                ticket.setClosedAt(now);
                ticketSla.onStatusChanged(ticket, TicketStatus.RESOLVED, TicketStatus.CLOSED);
                history.change(ticket, null, HistoryAction.STATUS_CHANGED, "status", TicketStatus.RESOLVED,
                        TicketStatus.CLOSED, "Clôture automatique : résolution sans réponse du demandeur");
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Auto-closed {} resolved ticket(s)", closed);
        }
        return closed;
    }
}
