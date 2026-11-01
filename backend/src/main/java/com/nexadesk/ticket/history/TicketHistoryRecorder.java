package com.nexadesk.ticket.history;

import com.nexadesk.ticket.Ticket;
import com.nexadesk.user.User;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Writes timeline entries in the same transaction as the change they describe. */
@Component
@RequiredArgsConstructor
public class TicketHistoryRecorder {

    private final TicketHistoryRepository repository;
    private final Clock clock;

    public void record(Ticket ticket, User actor, HistoryAction action, String comment) {
        save(ticket, actor, action, null, null, null, comment, false);
    }

    /** Records a field change; does nothing if the value did not actually change. */
    public void change(Ticket ticket, User actor, HistoryAction action, String field, Object oldValue,
                       Object newValue, String comment) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        save(ticket, actor, action, field, display(oldValue), display(newValue), comment, false);
    }

    public void recordInternal(Ticket ticket, User actor, HistoryAction action, String comment) {
        save(ticket, actor, action, null, null, null, comment, true);
    }

    private void save(Ticket ticket, User actor, HistoryAction action, String field, String oldValue,
                      String newValue, String comment, boolean internal) {
        repository.save(TicketHistory.builder()
                .ticket(ticket)
                .actor(actor)
                .action(action)
                .field(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .comment(comment)
                .internal(internal)
                .createdAt(clock.instant())
                .build());
    }

    private static String display(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Enum<?> e ? e.name() : value.toString();
    }
}
