package com.nexadesk.equipment;

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

/** One period during which a user held a device. */
@Entity
@Table(name = "equipment_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    public EquipmentAssignment(Equipment equipment, User user, Instant assignedAt) {
        this.equipment = equipment;
        this.user = user;
        this.assignedAt = assignedAt;
    }

    public void close(Instant at) {
        if (returnedAt == null) {
            returnedAt = at;
        }
    }
}
