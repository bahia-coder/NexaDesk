package com.nexadesk.reporting.dto;

import com.nexadesk.reporting.dto.AnalyticsDtos.CountItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.Satisfaction;
import com.nexadesk.reporting.dto.AnalyticsDtos.SlaCompliance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TechnicianPerformance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** All optional: the default period is the last 90 days. */
    public record ReportRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                Long categoryId, Long technicianId) {
    }

    /** The filters actually applied, with display names (shown in the exported files). */
    public record ReportScope(LocalDate from, LocalDate to, Long categoryId, String categoryName, Long technicianId,
                              String technicianName) {
    }

    public record ReportSummary(long created, long resolved, Double resolutionRate, Double avgResolutionHours,
                                SlaCompliance sla) {
    }

    public record Report(ReportScope scope, ReportSummary summary, List<CountItem> byCategory,
                         List<CountItem> byPriority, List<TechnicianPerformance> byTechnician,
                         List<TimelinePoint> monthly, Satisfaction satisfaction, Instant generatedAt) {
    }
}
