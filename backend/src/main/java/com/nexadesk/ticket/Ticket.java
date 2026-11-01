package com.nexadesk.ticket;

import com.nexadesk.category.Level;
import com.nexadesk.category.Priority;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketSubCategory;
import com.nexadesk.common.BaseEntity;
import com.nexadesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A support ticket. State changes go through {@code TicketWorkflowService}, which checks {@code TicketPolicy}
 * and records history; this entity only holds the data and its invariants.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TicketType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level urgency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "priority_id", nullable = false)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TicketCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private TicketSubCategory subcategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_reason", length = 20)
    private PendingReason pendingReason;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    @Version
    @Column(nullable = false)
    private int version;

    @Builder
    public Ticket(String reference, TicketType type, String title, String description, Level impact, Level urgency,
                  Priority priority, TicketCategory category, TicketSubCategory subcategory, User requester) {
        this.reference = reference;
        this.type = type;
        this.title = title;
        this.description = description;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = priority;
        this.category = category;
        this.subcategory = subcategory;
        this.requester = requester;
    }

    public boolean isRequestedBy(Long userId) {
        return requester.getId().equals(userId);
    }

    public boolean isAssignedTo(Long userId) {
        return assignee != null && assignee.getId().equals(userId);
    }
}
