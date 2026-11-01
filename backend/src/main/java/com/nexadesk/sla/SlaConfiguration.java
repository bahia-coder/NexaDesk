package com.nexadesk.sla;

import com.nexadesk.category.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** SLA rule of one priority. Seeded by Flyway (V6), edited by the administrator. */
@Entity
@Table(name = "sla_configurations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlaConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "priority_id", nullable = false, unique = true, updatable = false)
    private Priority priority;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "business_hours_only", nullable = false)
    private boolean businessHoursOnly;

    @Column(name = "warning_threshold_percent", nullable = false)
    private int warningThresholdPercent;

    @Version
    @Column(nullable = false)
    private int version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
