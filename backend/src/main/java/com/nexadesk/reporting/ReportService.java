package com.nexadesk.reporting;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.reporting.AnalyticsQueries.Granularity;
import com.nexadesk.reporting.AnalyticsQueries.Scope;
import com.nexadesk.reporting.dto.ReportDtos.Report;
import com.nexadesk.reporting.dto.ReportDtos.ReportRequest;
import com.nexadesk.reporting.dto.ReportDtos.ReportScope;
import com.nexadesk.reporting.dto.ReportDtos.ReportSummary;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Filterable activity report; the same {@link Report} feeds the JSON view and the Excel / PDF exports. */
@Service
public class ReportService {

    private static final int DEFAULT_DAYS = 90;
    private static final int MAX_DAYS = 731;

    private final AnalyticsQueries analytics;
    private final TicketCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(AnalyticsQueries analytics, TicketCategoryRepository categoryRepository,
                         UserRepository userRepository, CurrentUser currentUser, Clock clock,
                         @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.analytics = analytics;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    /** A technician only ever gets their own figures, whatever filter they send. */
    @Transactional(readOnly = true)
    public Report build(ReportRequest request) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate to = request.to() != null ? request.to() : today;
        LocalDate from = request.from() != null ? request.from() : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La date de début doit précéder la date de fin.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La période est limitée à deux ans.");
        }
        Long technicianId = currentUser.hasRole(RoleCode.TECHNICIAN) ? currentUser.id() : request.technicianId();

        String categoryName = null;
        if (request.categoryId() != null) {
            categoryName = categoryRepository.findById(request.categoryId()).map(TicketCategory::getName)
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie", request.categoryId()));
        }
        String technicianName = null;
        if (technicianId != null) {
            technicianName = userRepository.findById(technicianId).map(User::getFullName)
                    .orElseThrow(() -> new ResourceNotFoundException("Technicien", technicianId));
        }

        Scope scope = new Scope(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                request.categoryId(), technicianId);
        long created = analytics.createdCount(scope);
        long done = analytics.resolvedAmongCreated(scope);
        ReportSummary summary = new ReportSummary(created, analytics.resolvedCount(scope),
                created == 0 ? null : round(100.0 * done / created), round(analytics.avgResolutionHours(scope)),
                analytics.slaCompliance(scope));

        return new Report(new ReportScope(from, to, request.categoryId(), categoryName, technicianId, technicianName),
                summary, analytics.createdByCategory(scope), analytics.createdByPriority(scope),
                analytics.technicianPerformance(scope),
                Timelines.merge(analytics.createdTimeline(scope, Granularity.MONTH),
                        analytics.resolvedTimeline(scope, Granularity.MONTH), from, to, Granularity.MONTH),
                analytics.satisfaction(scope), clock.instant());
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
