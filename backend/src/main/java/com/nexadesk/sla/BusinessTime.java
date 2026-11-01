package com.nexadesk.sla;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Business-time arithmetic. Pure functions (no clock, no database): every SLA rule is unit-tested here.
 *
 * <p>In business-hours mode, time only flows during working hours of working days (holidays excluded): "24
 * business hours" with 08:00–18:00 days is about 2.4 working days. In 24/7 mode it is plain elapsed time.
 * Computations go through {@link ZonedDateTime} in the company time zone, so daylight-saving changes are
 * handled by java.time (a day with a 23 h clock still has 10 working hours).
 */
public final class BusinessTime {

    /** Protection against a calendar that would never contain working time. */
    private static final int MAX_DAYS_SCANNED = 3660;

    private BusinessTime() {
    }

    /** The instant reached after {@code amount} of business time from {@code start}. */
    public static Instant add(Instant start, Duration amount, WorkingCalendar calendar, boolean businessHoursOnly) {
        if (!businessHoursOnly) {
            return start.plus(amount);
        }
        if (amount.isZero() || amount.isNegative()) {
            return start;
        }
        Duration remaining = amount;
        ZonedDateTime cursor = start.atZone(calendar.zone());
        for (int scanned = 0; scanned < MAX_DAYS_SCANNED; scanned++) {
            LocalDate day = cursor.toLocalDate();
            if (calendar.isWorkingDay(day)) {
                ZonedDateTime open = at(day, calendar.workStart(), calendar);
                ZonedDateTime close = at(day, calendar.workEnd(), calendar);
                if (cursor.isBefore(open)) {
                    cursor = open;
                }
                if (cursor.isBefore(close)) {
                    Duration available = Duration.between(cursor, close);
                    if (available.compareTo(remaining) >= 0) {
                        return cursor.plus(remaining).toInstant();
                    }
                    remaining = remaining.minus(available);
                }
            }
            cursor = day.plusDays(1).atStartOfDay(calendar.zone());
        }
        throw new IllegalStateException("No business time found within " + MAX_DAYS_SCANNED + " days");
    }

    /** The instant from which {@code amount} of business time leads to {@code end} (inverse of {@link #add}). */
    public static Instant subtract(Instant end, Duration amount, WorkingCalendar calendar, boolean businessHoursOnly) {
        if (!businessHoursOnly) {
            return end.minus(amount);
        }
        if (amount.isZero() || amount.isNegative()) {
            return end;
        }
        Duration remaining = amount;
        ZonedDateTime cursor = end.atZone(calendar.zone());
        LocalDate day = cursor.toLocalDate();
        for (int scanned = 0; scanned < MAX_DAYS_SCANNED; scanned++) {
            if (calendar.isWorkingDay(day)) {
                ZonedDateTime open = at(day, calendar.workStart(), calendar);
                ZonedDateTime close = at(day, calendar.workEnd(), calendar);
                if (cursor.isAfter(close)) {
                    cursor = close;
                }
                if (cursor.isAfter(open)) {
                    Duration available = Duration.between(open, cursor);
                    if (available.compareTo(remaining) >= 0) {
                        return cursor.minus(remaining).toInstant();
                    }
                    remaining = remaining.minus(available);
                }
            }
            cursor = day.atStartOfDay(calendar.zone());
            day = day.minusDays(1);
        }
        throw new IllegalStateException("No business time found within " + MAX_DAYS_SCANNED + " days");
    }

    public static Instant addMinutes(Instant start, long minutes, WorkingCalendar calendar, boolean businessHoursOnly) {
        return add(start, Duration.ofMinutes(minutes), calendar, businessHoursOnly);
    }

    /** Business time elapsed between two instants (zero if {@code to} is not after {@code from}). */
    public static Duration between(Instant from, Instant to, WorkingCalendar calendar, boolean businessHoursOnly) {
        if (!to.isAfter(from)) {
            return Duration.ZERO;
        }
        if (!businessHoursOnly) {
            return Duration.between(from, to);
        }
        Duration total = Duration.ZERO;
        LocalDate day = from.atZone(calendar.zone()).toLocalDate();
        LocalDate last = to.atZone(calendar.zone()).toLocalDate();
        while (!day.isAfter(last)) {
            if (calendar.isWorkingDay(day)) {
                Instant open = at(day, calendar.workStart(), calendar).toInstant();
                Instant close = at(day, calendar.workEnd(), calendar).toInstant();
                Instant start = from.isAfter(open) ? from : open;
                Instant end = to.isBefore(close) ? to : close;
                if (end.isAfter(start)) {
                    total = total.plus(Duration.between(start, end));
                }
            }
            day = day.plusDays(1);
        }
        return total;
    }

    public static long minutesBetween(Instant from, Instant to, WorkingCalendar calendar, boolean businessHoursOnly) {
        return between(from, to, calendar, businessHoursOnly).toMinutes();
    }

    /** A local time on a given day; a time falling in a DST gap is shifted forward by java.time. */
    private static ZonedDateTime at(LocalDate day, LocalTime time, WorkingCalendar calendar) {
        return ZonedDateTime.of(day, time, calendar.zone());
    }
}
