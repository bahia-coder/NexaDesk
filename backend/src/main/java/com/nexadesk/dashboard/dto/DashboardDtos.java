package com.nexadesk.dashboard.dto;

import com.nexadesk.reporting.AnalyticsQueries.Granularity;
import com.nexadesk.reporting.dto.AnalyticsDtos.ActivityItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.CountItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.KpiValue;
import com.nexadesk.reporting.dto.AnalyticsDtos.Satisfaction;
import com.nexadesk.reporting.dto.AnalyticsDtos.SlaCompliance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TechnicianPerformance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Period(LocalDate from, LocalDate to, int days, Granularity granularity) {
    }

    /**
     * @param resolutionRate share (%) of the tickets created in the period that are now resolved or closed
     */
    public record AdminKpis(KpiValue created, KpiValue resolved, long open, long inProgress, long pending,
                            long overdue, long atRisk, long unassigned, Double avgResolutionHours,
                            Double avgResolutionHoursPrevious, Double resolutionRate) {
    }

    public record AdminDashboard(Period period, AdminKpis kpis, SlaCompliance sla, List<TimelinePoint> timeline,
                                 List<CountItem> byPriority, List<CountItem> byStatus, List<CountItem> byCategory,
                                 List<TechnicianPerformance> technicians, Satisfaction satisfaction,
                                 List<ActivityItem> recentActivity) {
    }

    public record TechnicianDashboard(long assignedOpen, long pending, long urgent, long overdue, long atRisk,
                                      long resolvedThisWeek, long unassignedInSpecialties, long minutesThisWeek,
                                      Double avgResolutionHours, Double slaRate, List<CountItem> byCategory,
                                      List<TicketSummaryDto> queue, List<ActivityItem> recentActivity) {
    }

    public record EmployeeDashboard(long waiting, long inProgress, long resolved, long toConfirm,
                                    List<TicketSummaryDto> awaitingConfirmation, List<TicketSummaryDto> recent) {
    }
}
