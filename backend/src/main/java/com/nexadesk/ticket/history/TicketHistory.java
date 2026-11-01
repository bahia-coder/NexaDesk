package com.nexadesk.ticket.history;

import com.nexadesk.ticket.Ticket;
import com.nexadesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only timeline entry. Old/new values are display strings (labels, names), truncated to 500 chars. */
@Entity
@Table(name = "ticket_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Null for actions performed by the system. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HistoryAction action;

    @Column(length = 50)
    private String field;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public TicketHistory(Ticket ticket, User actor, HistoryAction action, String field, String oldValue,
                         String newValue, String comment, boolean internal, Instant createdAt) {
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.field = field;
        this.oldValue = truncate(oldValue, 500);
        this.newValue = truncate(newValue, 500);
        this.comment = truncate(comment, 1000);
        this.internal = internal;
        this.createdAt = createdAt;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
