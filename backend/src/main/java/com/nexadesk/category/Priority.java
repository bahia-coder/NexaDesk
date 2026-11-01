package com.nexadesk.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reference data seeded by Flyway (V3). Code and rank are fixed; label and color are configurable. */
@Entity
@Table(name = "priorities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Priority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PriorityCode code;

    @Setter
    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, unique = true)
    private int rank;

    @Setter
    @Column(nullable = false, length = 7)
    private String color;
}
