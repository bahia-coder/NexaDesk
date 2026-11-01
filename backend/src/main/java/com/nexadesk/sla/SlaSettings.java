package com.nexadesk.sla;

import com.nexadesk.category.PriorityCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * In-memory snapshot of the SLA rules and calendar. They are read for every ticket shown in a list but change
 * rarely, so they are loaded once and reloaded after an administrator's change has been committed.
 */
@Component
public class SlaSettings {

    public record Rule(int firstResponseMinutes, int resolutionMinutes, boolean businessHoursOnly,
                       int warningThresholdPercent) {
    }

    public record Snapshot(WorkingCalendar calendar, Map<PriorityCode, Rule> rules) {

        public Rule rule(PriorityCode priority) {
            Rule rule = rules.get(priority);
            if (rule == null) {
                throw new IllegalStateException("No SLA rule for priority " + priority);
            }
            return rule;
        }
    }

    private final SlaConfigurationRepository configurations;
    private final BusinessCalendarRepository calendars;
    private final HolidayRepository holidays;
    private final TransactionTemplate readOnly;
    private final ZoneId zone;
    private volatile Snapshot snapshot;

    public SlaSettings(SlaConfigurationRepository configurations, BusinessCalendarRepository calendars,
                       HolidayRepository holidays, TransactionTemplate transactionTemplate,
                       @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.configurations = configurations;
        this.calendars = calendars;
        this.holidays = holidays;
        this.readOnly = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.readOnly.setReadOnly(true);
        this.zone = zone;
    }

    public Snapshot current() {
        Snapshot current = snapshot;
        if (current == null) {
            current = readOnly.execute(status -> load());
            snapshot = current;
        }
        return current;
    }

    /** Drops the snapshot once the current transaction commits (immediately outside a transaction). */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    snapshot = null;
                }
            });
        } else {
            snapshot = null;
        }
    }

    private Snapshot load() {
        BusinessCalendar calendar = calendars.findById(BusinessCalendar.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("business_calendar row is missing"));
        Set<LocalDate> holidayDays = holidays.findAll().stream().map(Holiday::getDay).collect(Collectors.toSet());
        Map<PriorityCode, Rule> rules = new EnumMap<>(PriorityCode.class);
        for (SlaConfiguration c : configurations.findAllWithPriority()) {
            rules.put(c.getPriority().getCode(), new Rule(c.getFirstResponseMinutes(), c.getResolutionMinutes(),
                    c.isBusinessHoursOnly(), c.getWarningThresholdPercent()));
        }
        return new Snapshot(new WorkingCalendar(zone, calendar.getWorkStart(), calendar.getWorkEnd(),
                calendar.getWorkingDays(), holidayDays), rules);
    }
}
