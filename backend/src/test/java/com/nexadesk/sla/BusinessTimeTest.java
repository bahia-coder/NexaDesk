package com.nexadesk.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** November 2026: Monday 2nd … Friday 6th; Wednesday 11th is a holiday. Paris switches to winter time on Oct 25th. */
class BusinessTimeTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final WorkingCalendar CALENDAR = new WorkingCalendar(PARIS, LocalTime.of(8, 0), LocalTime.of(18, 0),
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Set.of(LocalDate.of(2026, 11, 11)));

    private static Instant paris(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(PARIS).toInstant();
    }

    private static Instant addBusiness(String start, long minutes) {
        return BusinessTime.addMinutes(paris(start), minutes, CALENDAR, true);
    }

    @Test
    void staysWithinTheSameWorkingDay() {
        assertThat(addBusiness("2026-11-02T10:00", 60)).isEqualTo(paris("2026-11-02T11:00"));
        assertThat(addBusiness("2026-11-02T10:00", 480)).as("ends exactly at closing").isEqualTo(paris("2026-11-02T18:00"));
    }

    @Test
    void continuesOnTheNextWorkingDayAfterClosing() {
        assertThat(addBusiness("2026-11-02T17:30", 60)).isEqualTo(paris("2026-11-03T08:30"));
    }

    @Test
    void startsAtOpeningWhenCreatedBeforeIt() {
        assertThat(addBusiness("2026-11-02T06:00", 30)).isEqualTo(paris("2026-11-02T08:30"));
    }

    @Test
    void skipsWeekends() {
        assertThat(addBusiness("2026-11-06T19:00", 60)).isEqualTo(paris("2026-11-09T09:00"));
        assertThat(addBusiness("2026-11-07T11:00", 30)).as("created on Saturday").isEqualTo(paris("2026-11-09T08:30"));
    }

    @Test
    void skipsHolidays() {
        assertThat(addBusiness("2026-11-10T17:00", 120)).isEqualTo(paris("2026-11-12T09:00"));
    }

    @Test
    void twentyFourBusinessHoursSpanAboutTwoAndAHalfWorkingDays() {
        assertThat(addBusiness("2026-11-02T08:00", 24 * 60)).isEqualTo(paris("2026-11-04T12:00"));
    }

    @Test
    void roundTheClockModeIgnoresTheCalendar() {
        assertThat(BusinessTime.addMinutes(paris("2026-11-07T23:30"), 120, CALENDAR, false))
                .isEqualTo(paris("2026-11-08T01:30"));
    }

    @Test
    void handlesTheDaylightSavingTimeChange() {
        // Friday 23 Oct 17:00 (summer time) + 2 h → Monday 26 Oct 09:00 (winter time).
        assertThat(addBusiness("2026-10-23T17:00", 120)).isEqualTo(paris("2026-10-26T09:00"));
        assertThat(BusinessTime.minutesBetween(paris("2026-10-23T08:00"), paris("2026-10-26T18:00"), CALENDAR, true))
                .as("two full working days on both sides of the change").isEqualTo(20 * 60);
    }

    @Test
    void measuresOnlyWorkingTimeBetweenTwoInstants() {
        assertThat(BusinessTime.minutesBetween(paris("2026-11-06T17:00"), paris("2026-11-09T09:00"), CALENDAR, true))
                .isEqualTo(120);
        assertThat(BusinessTime.minutesBetween(paris("2026-11-10T12:00"), paris("2026-11-12T12:00"), CALENDAR, true))
                .as("holiday in between").isEqualTo(10 * 60);
        assertThat(BusinessTime.minutesBetween(paris("2026-11-02T20:00"), paris("2026-11-03T07:00"), CALENDAR, true))
                .as("night only").isZero();
        assertThat(BusinessTime.between(paris("2026-11-03T10:00"), paris("2026-11-02T10:00"), CALENDAR, true))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void addAndBetweenAreConsistent() {
        Instant start = paris("2026-11-05T15:20");
        for (long minutes : new long[] {1, 45, 160, 600, 1440, 2880}) {
            Instant end = BusinessTime.addMinutes(start, minutes, CALENDAR, true);
            assertThat(BusinessTime.minutesBetween(start, end, CALENDAR, true)).as("%d minutes", minutes)
                    .isEqualTo(minutes);
        }
    }

    @Test
    void subtractWalksBackOverNightsWeekendsAndHolidays() {
        assertThat(BusinessTime.subtract(paris("2026-11-09T09:00"), Duration.ofMinutes(120), CALENDAR, true))
                .isEqualTo(paris("2026-11-06T17:00"));
        assertThat(BusinessTime.subtract(paris("2026-11-12T09:00"), Duration.ofMinutes(120), CALENDAR, true))
                .as("skips holiday Wednesday 11th").isEqualTo(paris("2026-11-10T17:00"));
        assertThat(BusinessTime.subtract(paris("2026-11-08T22:00"), Duration.ofMinutes(30), CALENDAR, true))
                .as("from a Sunday evening").isEqualTo(paris("2026-11-06T17:30"));
        assertThat(BusinessTime.subtract(paris("2026-11-08T01:30"), Duration.ofMinutes(120), CALENDAR, false))
                .isEqualTo(paris("2026-11-07T23:30"));
    }

    @Test
    void subtractIsTheInverseOfAdd() {
        Instant start = paris("2026-11-05T15:20");
        for (long minutes : new long[] {1, 45, 160, 600, 1440, 2880}) {
            Instant end = BusinessTime.addMinutes(start, minutes, CALENDAR, true);
            assertThat(BusinessTime.subtract(end, Duration.ofMinutes(minutes), CALENDAR, true)).as("%d minutes", minutes)
                    .isEqualTo(start);
        }
    }

    @Test
    void rejectsACalendarWithoutWorkingTime() {
        assertThatThrownBy(() -> new WorkingCalendar(PARIS, LocalTime.of(18, 0), LocalTime.of(8, 0),
                EnumSet.of(DayOfWeek.MONDAY), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkingCalendar(PARIS, LocalTime.of(8, 0), LocalTime.of(18, 0),
                EnumSet.noneOf(DayOfWeek.class), Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
