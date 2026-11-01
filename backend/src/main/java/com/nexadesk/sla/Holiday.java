package com.nexadesk.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A non-working day: business-time SLA clocks do not run on it. */
@Entity
@Table(name = "holidays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate day;

    @Column(nullable = false, length = 100)
    private String label;

    public Holiday(LocalDate day, String label) {
        this.day = day;
        this.label = label;
    }
}
