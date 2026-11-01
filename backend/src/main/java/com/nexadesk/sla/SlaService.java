package com.nexadesk.sla;

import com.nexadesk.ticket.SlaState;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketSla;
import com.nexadesk.ticket.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * SLA clock of a ticket.
 *
 * <ul>
 *   <li><b>First response</b>: due {@code firstResponseMinutes} after creation; met by the first start of work or
 *       the first public answer from the IT team. Never paused.</li>
 *   <li><b>Resolution</b>: due {@code resolutionMinutes} after creation. The clock stops while the ticket is
 *       PENDING, RESOLVED or CLOSED; when it restarts, the deadline is pushed back by the paused business time.
 *       A rejected resolution or a reopening therefore does not count the time spent waiting or closed.</li>
 * </ul>
 * All durations are business time or 24/7 depending on the priority's rule (see {@link BusinessTime}).
 */
@Service
public class SlaService implements TicketSla {

    /** Statuses in which the resolution clock does not run. */
    static final Set<TicketStatus> PAUSED = EnumSet.of(TicketStatus.PENDING, TicketStatus.RESOLVED,
            TicketStatus.CLOSED, TicketStatus.REJECTED);

    private final SlaSettings settings;
    private final Clock clock;

    public SlaService(SlaSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void onCreated(Ticket ticket, Instant createdAt) {
        SlaSettings.Snapshot snapshot = settings.current();
        SlaSettings.Rule rule = snapshot.rule(ticket.getPriority().getCode());
        ticket.setSlaBusinessHours(rule.businessHoursOnly());
        ticket.setSlaPausedMinutes(0);
        computeDeadlines(ticket, createdAt, rule, snapshot.calendar());
    }

    @Override
    public void onPriorityChanged(Ticket ticket) {
        if (ticket.getResolutionDueAt() == null) {
            return;
        }
        SlaSettings.Snapshot snapshot = settings.current();
        SlaSettings.Rule rule = snapshot.rule(ticket.getPriority().getCode());
        // The paused time was measured in the previous mode; it is kept as is (minutes).
        ticket.setSlaBusinessHours(rule.businessHoursOnly());
        computeDeadlines(ticket, ticket.getCreatedAt(), rule, snapshot.calendar());
    }

    @Override
    public void onStatusChanged(Ticket ticket, TicketStatus from, TicketStatus to) {
        if (ticket.getResolutionDueAt() == null) {
            return;
        }
        Instant now = clock.instant();
        if (to == TicketStatus.IN_PROGRESS && ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(now);
        }
        boolean pausedNow = PAUSED.contains(to);
        if (pausedNow && ticket.getSlaPausedAt() == null) {
            ticket.setSlaPausedAt(now);
        } else if (!pausedNow && ticket.getSlaPausedAt() != null) {
            resume(ticket, now);
        }
    }

    @Override
    public void onStaffReply(Ticket ticket) {
        if (ticket.getResolutionDueAt() != null && ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(clock.instant());
        }
    }

    @Override
    public View view(Ticket ticket) {
        if (ticket.getResolutionDueAt() == null || ticket.getStatus() == TicketStatus.REJECTED) {
            return View.NONE;
        }
        SlaSettings.Snapshot snapshot = settings.current();
        WorkingCalendar calendar = snapshot.calendar();
        boolean businessHours = Boolean.TRUE.equals(ticket.getSlaBusinessHours());
        Instant now = clock.instant();

        Instant firstResponseReference = ticket.getFirstRespondedAt() != null ? ticket.getFirstRespondedAt() : now;
        boolean firstResponseBreached = ticket.getFirstResponseDueAt() != null
                && firstResponseReference.isAfter(ticket.getFirstResponseDueAt())
                && (ticket.getFirstRespondedAt() != null || ticket.getStatus().isOpen());

        // Time consumed on the resolution clock, excluding pauses (the deadline was extended by the same amount).
        Instant clockStoppedAt = ticket.getSlaPausedAt() != null ? ticket.getSlaPausedAt() : now;
        long target = BusinessTime.minutesBetween(ticket.getCreatedAt(), ticket.getResolutionDueAt(), calendar,
                businessHours) - ticket.getSlaPausedMinutes();
        long elapsed = BusinessTime.minutesBetween(ticket.getCreatedAt(), clockStoppedAt, calendar, businessHours)
                - ticket.getSlaPausedMinutes();
        Integer consumed = target <= 0 ? null : (int) Math.max(0, Math.min(999, elapsed * 100 / target));

        SlaState state = state(ticket, now, consumed, snapshot);
        return new View(state, ticket.getFirstResponseDueAt(), ticket.getFirstRespondedAt(), firstResponseBreached,
                ticket.getResolutionDueAt(), consumed, businessHours);
    }

    private static SlaState state(Ticket ticket, Instant now, Integer consumed, SlaSettings.Snapshot snapshot) {
        Instant due = ticket.getResolutionDueAt();
        return switch (ticket.getStatus()) {
            case RESOLVED, CLOSED -> ticket.getResolvedAt() != null && !ticket.getResolvedAt().isAfter(due)
                    ? SlaState.MET : SlaState.MISSED;
            case PENDING -> ticket.getSlaPausedAt() != null && ticket.getSlaPausedAt().isAfter(due)
                    ? SlaState.BREACHED : SlaState.PAUSED;
            case REJECTED -> SlaState.NONE;
            case NEW, ASSIGNED, IN_PROGRESS -> {
                if (now.isAfter(due)) {
                    yield SlaState.BREACHED;
                }
                int threshold = snapshot.rule(ticket.getPriority().getCode()).warningThresholdPercent();
                yield consumed != null && consumed >= threshold ? SlaState.AT_RISK : SlaState.ON_TRACK;
            }
        };
    }

    private void resume(Ticket ticket, Instant now) {
        WorkingCalendar calendar = settings.current().calendar();
        boolean businessHours = Boolean.TRUE.equals(ticket.getSlaBusinessHours());
        long paused = BusinessTime.minutesBetween(ticket.getSlaPausedAt(), now, calendar, businessHours);
        ticket.setSlaPausedMinutes(ticket.getSlaPausedMinutes() + (int) paused);
        ticket.setResolutionDueAt(BusinessTime.addMinutes(ticket.getResolutionDueAt(), paused, calendar,
                businessHours));
        ticket.setSlaPausedAt(null);
        // Pushed back before its deadline again: the thresholds can be crossed anew, so alerts are re-armed.
        // A ticket still overdue keeps its flags (no duplicate alert).
        if (ticket.getResolutionDueAt().isAfter(now)) {
            ticket.setSlaWarningSentAt(null);
            ticket.setSlaBreachSentAt(null);
        }
    }

    private static void computeDeadlines(Ticket ticket, Instant created, SlaSettings.Rule rule,
                                         WorkingCalendar calendar) {
        boolean businessHours = rule.businessHoursOnly();
        ticket.setFirstResponseDueAt(BusinessTime.addMinutes(created, rule.firstResponseMinutes(), calendar,
                businessHours));
        ticket.setResolutionDueAt(BusinessTime.addMinutes(created,
                (long) rule.resolutionMinutes() + ticket.getSlaPausedMinutes(), calendar, businessHours));
    }
}
