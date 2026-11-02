package com.nexadesk.dashboard;

import com.nexadesk.dashboard.dto.DashboardDtos.AdminDashboard;
import com.nexadesk.dashboard.dto.DashboardDtos.AdminKpis;
import com.nexadesk.dashboard.dto.DashboardDtos.EmployeeDashboard;
import com.nexadesk.dashboard.dto.DashboardDtos.Period;
import com.nexadesk.dashboard.dto.DashboardDtos.TechnicianDashboard;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.reporting.AnalyticsQueries;
import com.nexadesk.reporting.AnalyticsQueries.CurrentState;
import com.nexadesk.reporting.AnalyticsQueries.Granularity;
import com.nexadesk.reporting.AnalyticsQueries.RequesterCounts;
import com.nexadesk.reporting.AnalyticsQueries.Scope;
import com.nexadesk.reporting.Timelines;
import com.nexadesk.reporting.dto.AnalyticsDtos.KpiValue;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.ticket.TicketService;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.dto.TicketDtos.TicketFilter;
import com.nexadesk.ticket.dto.TicketDtos.TicketScope;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Role-specific dashboards. Every figure comes from {@link AnalyticsQueries}: nothing is hard-coded. */
@Service
public class DashboardService {

    static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90, 365);
    private static final List<TicketStatus> ASSIGNED_OPEN = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
            TicketStatus.PENDING);

    private final AnalyticsQueries analytics;
    private final TicketService ticketService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(AnalyticsQueries analytics, TicketService ticketService, CurrentUser currentUser,
                            Clock clock, @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.analytics = analytics;
        this.ticketService = ticketService;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public AdminDashboard admin(int days) {
        if (!ALLOWED_PERIODS.contains(days)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Période autorisée : 7, 30, 90 ou 365 jours.");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate fromDate = today.minusDays(days - 1L);
        Instant from = fromDate.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant previousFrom = fromDate.minusDays(days).atStartOfDay(zone).toInstant();
        Scope current = Scope.of(from, to);
        Scope previous = Scope.of(previousFrom, from);
        Granularity granularity = days <= 31 ? Granularity.DAY : days <= 120 ? Granularity.WEEK : Granularity.MONTH;

        long created = analytics.createdCount(current);
        long resolved = analytics.resolvedCount(current);
        CurrentState state = analytics.currentState(null, null, now);
        long createdAndDone = analytics.resolvedAmongCreated(current);

        AdminKpis kpis = new AdminKpis(
                new KpiValue(created, change(created, analytics.createdCount(previous))),
                new KpiValue(resolved, change(resolved, analytics.resolvedCount(previous))),
                state.open(), state.inProgress(), state.pending(), state.overdue(), state.atRisk(), state.unassigned(),
                round(analytics.avgResolutionHours(current)), round(analytics.avgResolutionHours(previous)),
                created == 0 ? null : round(100.0 * createdAndDone / created));

        return new AdminDashboard(new Period(fromDate, today, days, granularity), kpis, analytics.slaCompliance(current),
                Timelines.merge(analytics.createdTimeline(current, granularity),
                        analytics.resolvedTimeline(current, granularity), fromDate, today, granularity),
                analytics.createdByPriority(current), analytics.byStatus(null, null),
                analytics.createdByCategory(current), analytics.technicianPerformance(current),
                analytics.satisfaction(current), analytics.recentActivity(10, null));
    }

    @Transactional(readOnly = true)
    public TechnicianDashboard technician() {
        Long me = currentUser.id();
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        Instant tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
        Scope last30Days = new Scope(today.minusDays(29).atStartOfDay(zone).toInstant(), tomorrow, null, me);

        CurrentState state = analytics.currentState(null, me, now);
        List<TicketSummaryDto> queue = ticketService.list(
                filter(ASSIGNED_OPEN, TicketScope.ASSIGNED_TO_ME),
                PageRequest.of(0, 6, Sort.by(Sort.Order.asc("resolutionDueAt")))).content();

        return new TechnicianDashboard(state.open(), state.pending(), analytics.urgentAssigned(me, now),
                state.overdue(), state.atRisk(),
                analytics.resolvedCount(new Scope(weekStart, tomorrow, null, me)), analytics.unassignedInSpecialties(me),
                analytics.minutesLogged(me, weekStart, tomorrow), round(analytics.avgResolutionHours(last30Days)),
                round(analytics.slaCompliance(last30Days).resolutionRate()),
                analytics.createdByCategory(last30Days).stream().filter(item -> item.count() > 0).toList(),
                queue, analytics.recentActivity(8, me));
    }

    @Transactional(readOnly = true)
    public EmployeeDashboard employee() {
        RequesterCounts counts = analytics.requesterCounts(currentUser.id());
        List<TicketSummaryDto> toConfirm = ticketService.list(filter(List.of(TicketStatus.RESOLVED), TicketScope.MINE),
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("updatedAt")))).content();
        List<TicketSummaryDto> recent = ticketService.list(filter(null, TicketScope.MINE),
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("createdAt")))).content();
        return new EmployeeDashboard(counts.waiting(), counts.inProgress(), counts.resolved(), counts.toConfirm(),
                toConfirm, recent);
    }

    private static TicketFilter filter(List<TicketStatus> statuses, TicketScope scope) {
        return new TicketFilter(null, statuses, null, null, null, null, null, scope, null, null, null);
    }

    /** Percentage variation, one decimal; null when the previous period is empty. */
    static Double change(long current, long previous) {
        return previous == 0 ? null : round(100.0 * (current - previous) / previous);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
