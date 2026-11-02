package com.nexadesk.sla;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.sla.dto.SlaDtos.CalendarDto;
import com.nexadesk.sla.dto.SlaDtos.CalendarRequest;
import com.nexadesk.sla.dto.SlaDtos.HolidayDto;
import com.nexadesk.sla.dto.SlaDtos.HolidayRequest;
import com.nexadesk.sla.dto.SlaDtos.SlaPreviewDto;
import com.nexadesk.sla.dto.SlaDtos.SlaRuleDto;
import com.nexadesk.sla.dto.SlaDtos.SlaRuleRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of SLA rules, working hours and holidays. A rule change applies to tickets created afterwards;
 * existing tickets keep the deadlines computed at their creation.
 */
@Service
public class SlaAdminService {

    private final SlaConfigurationRepository ruleRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final SlaSettings settings;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    public SlaAdminService(SlaConfigurationRepository ruleRepository, BusinessCalendarRepository calendarRepository,
                           HolidayRepository holidayRepository, SlaSettings settings, AuditService audit, Clock clock,
                           @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.ruleRepository = ruleRepository;
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public List<SlaRuleDto> rules() {
        return ruleRepository.findAllWithPriority().stream().map(SlaAdminService::toDto).toList();
    }

    @Transactional
    public SlaRuleDto updateRule(Long id, SlaRuleRequest request) {
        if (request.resolutionMinutes() <= request.firstResponseMinutes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Le délai de résolution doit être supérieur au délai de première réponse.");
        }
        SlaConfiguration rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Règle SLA", id));
        Map<String, Object> before = snapshot(rule);
        rule.setFirstResponseMinutes(request.firstResponseMinutes());
        rule.setResolutionMinutes(request.resolutionMinutes());
        rule.setBusinessHoursOnly(request.businessHoursOnly());
        rule.setWarningThresholdPercent(request.warningThresholdPercent());
        ruleRepository.flush();
        settings.invalidateAfterCommit();
        audit.record(AuditAction.SLA_RULE_UPDATED, "SLA_RULE", rule.getPriority().getCode(),
                "Règle SLA " + rule.getPriority().getCode() + " modifiée", before, snapshot(rule));
        return toDto(rule);
    }

    @Transactional(readOnly = true)
    public CalendarDto calendar() {
        return toDto(calendarEntity());
    }

    @Transactional
    public CalendarDto updateCalendar(CalendarRequest request) {
        if (!request.workEnd().isAfter(request.workStart())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La fin de journée doit suivre son début.");
        }
        BusinessCalendar calendar = calendarEntity();
        CalendarDto before = toDto(calendar);
        calendar.setWorkStart(request.workStart());
        calendar.setWorkEnd(request.workEnd());
        calendar.setWorkingDays(request.workingDays());
        settings.invalidateAfterCommit();
        audit.record(AuditAction.SLA_CALENDAR_UPDATED, "SLA_CALENDAR", null, "Horaires ouvrés modifiés",
                AuditService.values("workStart", before.workStart().toString(), "workEnd",
                        before.workEnd().toString(), "workingDays", before.workingDays().toString()),
                AuditService.values("workStart", request.workStart().toString(), "workEnd",
                        request.workEnd().toString(), "workingDays", request.workingDays().toString()));
        return toDto(calendar);
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> holidays() {
        return holidayRepository.findAllByOrderByDayAsc().stream()
                .map(h -> new HolidayDto(h.getId(), h.getDay(), h.getLabel()))
                .toList();
    }

    @Transactional
    public HolidayDto addHoliday(HolidayRequest request) {
        if (holidayRepository.existsByDay(request.day())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "Ce jour est déjà déclaré férié.");
        }
        Holiday holiday = holidayRepository.save(new Holiday(request.day(), request.label().trim()));
        settings.invalidateAfterCommit();
        audit.record(AuditAction.HOLIDAY_ADDED, "HOLIDAY", request.day(),
                "Jour férié ajouté : " + request.day() + " (" + holiday.getLabel() + ")");
        return new HolidayDto(holiday.getId(), holiday.getDay(), holiday.getLabel());
    }

    @Transactional
    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jour férié", id));
        holidayRepository.delete(holiday);
        settings.invalidateAfterCommit();
        audit.record(AuditAction.HOLIDAY_REMOVED, "HOLIDAY", holiday.getDay(),
                "Jour férié supprimé : " + holiday.getDay() + " (" + holiday.getLabel() + ")");
    }

    /** Deadlines a ticket of this priority would get if created at {@code from} (default: now). */
    public SlaPreviewDto preview(PriorityCode priority, Instant from) {
        SlaSettings.Snapshot snapshot = settings.current();
        SlaSettings.Rule rule = snapshot.rule(priority);
        Instant start = from != null ? from : clock.instant();
        return new SlaPreviewDto(priority, start, rule.businessHoursOnly(),
                BusinessTime.addMinutes(start, rule.firstResponseMinutes(), snapshot.calendar(),
                        rule.businessHoursOnly()),
                BusinessTime.addMinutes(start, rule.resolutionMinutes(), snapshot.calendar(),
                        rule.businessHoursOnly()));
    }

    private BusinessCalendar calendarEntity() {
        return calendarRepository.findById(BusinessCalendar.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("business_calendar row is missing"));
    }

    private CalendarDto toDto(BusinessCalendar calendar) {
        return new CalendarDto(zone.getId(), calendar.getWorkStart(), calendar.getWorkEnd(),
                calendar.getWorkingDays());
    }

    private static Map<String, Object> snapshot(SlaConfiguration c) {
        return AuditService.values("firstResponseMinutes", c.getFirstResponseMinutes(), "resolutionMinutes",
                c.getResolutionMinutes(), "businessHoursOnly", c.isBusinessHoursOnly(), "warningThresholdPercent",
                c.getWarningThresholdPercent());
    }

    private static SlaRuleDto toDto(SlaConfiguration c) {
        return new SlaRuleDto(c.getId(), c.getPriority().getCode(), c.getPriority().getLabel(),
                c.getPriority().getColor(), c.getFirstResponseMinutes(), c.getResolutionMinutes(),
                c.isBusinessHoursOnly(), c.getWarningThresholdPercent());
    }
}
