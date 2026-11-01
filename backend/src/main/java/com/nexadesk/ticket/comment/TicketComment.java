package com.nexadesk.ticket.comment;

import com.nexadesk.ticket.Ticket;
import com.nexadesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable once posted (the history must reflect what the requester actually read). */
@Entity
@Table(name = "ticket_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** Internal notes are visible to technicians and admins only. */
    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TicketComment(Ticket ticket, User author, String body, boolean internal, Instant createdAt) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.internal = internal;
        this.createdAt = createdAt;
    }
}
