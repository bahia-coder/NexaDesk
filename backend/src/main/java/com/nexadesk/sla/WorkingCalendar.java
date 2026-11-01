package com.nexadesk.sla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/** Immutable view of the company's business hours, used by {@link BusinessTime}. */
public record WorkingCalendar(ZoneId zone, LocalTime workStart, LocalTime workEnd, Set<DayOfWeek> workingDays,
                              Set<LocalDate> holidays) {

    public WorkingCalendar {
        Objects.requireNonNull(zone);
        if (!workEnd.isAfter(workStart)) {
            throw new IllegalArgumentException("Working hours must end after they start");
        }
        if (workingDays.isEmpty()) {
            throw new IllegalArgumentException("At least one working day is required");
        }
        workingDays = Set.copyOf(workingDays);
        holidays = Set.copyOf(holidays);
    }

    public boolean isWorkingDay(LocalDate day) {
        return workingDays.contains(day.getDayOfWeek()) && !holidays.contains(day);
    }
}
