package com.nexadesk.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ITIL impact × urgency matrix. Employees describe impact and urgency; the priority is derived, so not every
 * ticket gets declared "critical". Technicians and admins can still override it (with a traced reason).
 *
 * <pre>
 *                 urgency LOW   MEDIUM   HIGH
 * impact HIGH       MEDIUM      HIGH     CRITICAL
 * impact MEDIUM     LOW         MEDIUM   HIGH
 * impact LOW        LOW         LOW      MEDIUM
 * </pre>
 */
public final class PriorityMatrix {

    public record Entry(Level impact, Level urgency, PriorityCode priority) {
    }

    private PriorityMatrix() {
    }

    public static PriorityCode priorityFor(Level impact, Level urgency) {
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(urgency, "urgency");
        int score = impact.ordinal() + urgency.ordinal(); // 0..4
        return switch (score) {
            case 0, 1 -> PriorityCode.LOW;
            case 2 -> PriorityCode.MEDIUM;
            case 3 -> PriorityCode.HIGH;
            default -> PriorityCode.CRITICAL;
        };
    }

    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Level impact : Level.values()) {
            for (Level urgency : Level.values()) {
                entries.add(new Entry(impact, urgency, priorityFor(impact, urgency)));
            }
        }
        return entries;
    }
}
