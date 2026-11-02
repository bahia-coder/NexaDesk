package com.nexadesk.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Building blocks shared by the dashboards and the reports. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /**
     * @param changePercent variation against the previous period of the same length; null when that period
     *                      had nothing to compare with
     */
    public record KpiValue(long value, Double changePercent) {
    }

    /** A labelled count (by priority, category, status…). {@code color} is set when the item has one. */
    public record CountItem(String key, String label, String color, long count) {
    }

    public record TimelinePoint(LocalDate bucket, long created, long resolved) {
    }

    /** Rates are percentages (0-100), null when nothing was measured. */
    public record SlaCompliance(Double firstResponseRate, Double resolutionRate, long resolvedMeasured) {
    }

    /** {@code distribution} holds the number of 1, 2, 3, 4 and 5-star ratings. */
    public record Satisfaction(Double average, long count, List<Long> distribution) {
    }

    public record TechnicianPerformance(Long id, String fullName, String avatarColor, long openAssigned,
                                        long resolved, Double avgResolutionHours, Double slaRate, Double avgRating,
                                        long minutesLogged) {
    }

    public record ActivityItem(Instant at, String action, Long ticketId, String ticketReference, String actorName,
                               String field, String oldValue, String newValue, String comment) {
    }
}
