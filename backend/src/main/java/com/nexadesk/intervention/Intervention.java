package com.nexadesk.intervention;

import com.nexadesk.common.BaseEntity;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One work session of a technician on a ticket. */
@Entity
@Table(name = "interventions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Intervention extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false, updatable = false)
    private User technician;

    @Column(nullable = false, columnDefinition = "text")
    private String diagnosis;

    @Column(columnDefinition = "text")
    private String solution;

    @Column(name = "time_spent_minutes", nullable = false)
    private int timeSpentMinutes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public Intervention(Ticket ticket, User technician) {
        this.ticket = ticket;
        this.technician = technician;
    }
}
