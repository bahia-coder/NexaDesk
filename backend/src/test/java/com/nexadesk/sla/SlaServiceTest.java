package com.nexadesk.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexadesk.category.Priority;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.support.MutableClock;
import com.nexadesk.ticket.SlaState;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketStatus;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** HIGH rule: first response 1 h, resolution 8 business hours, alert at 75 %. Monday 2 Nov 2026, Paris. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlaServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final WorkingCalendar CALENDAR = new WorkingCalendar(PARIS, LocalTime.of(8, 0), LocalTime.of(18, 0),
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Set.of());
    private static final SlaSettings.Snapshot SNAPSHOT = new SlaSettings.Snapshot(CALENDAR, Map.of(
            PriorityCode.HIGH, new SlaSettings.Rule(60, 480, true, 75),
            PriorityCode.CRITICAL, new SlaSettings.Rule(15, 120, false, 75)));

    @Mock
    private SlaSettings settings;

    private final MutableClock clock = new MutableClock(paris("2026-11-02T10:00"));
    private SlaService service;
    private Ticket ticket;

    private static Instant paris(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(PARIS).toInstant();
    }

    private static Priority priority(PriorityCode code) {
        Priority priority = BeanUtils.instantiateClass(Priority.class);
        ReflectionTestUtils.setField(priority, "code", code);
        return priority;
    }

    @BeforeEach
    void setUp() {
        when(settings.current()).thenReturn(SNAPSHOT);
        service = new SlaService(settings, clock);
        ticket = Ticket.builder().reference("INC-2026-0001").priority(priority(PriorityCode.HIGH)).build();
        ReflectionTestUtils.setField(ticket, "createdAt", clock.instant());
        service.onCreated(ticket, clock.instant());
    }

    private void moveTo(TicketStatus target) {
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(target);
        service.onStatusChanged(ticket, previous, target);
    }

    @Test
    void deadlinesAreComputedInBusinessTimeAtCreation() {
        assertThat(ticket.getFirstResponseDueAt()).isEqualTo(paris("2026-11-02T11:00"));
        assertThat(ticket.getResolutionDueAt()).isEqualTo(paris("2026-11-02T18:00"));
        assertThat(ticket.getSlaBusinessHours()).isTrue();
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    void startingWorkRecordsTheFirstResponseOnce() {
        moveTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getFirstRespondedAt()).isNull();

        clock.advance(Duration.ofMinutes(20));
        moveTo(TicketStatus.IN_PROGRESS);
        Instant firstResponse = ticket.getFirstRespondedAt();
        assertThat(firstResponse).isEqualTo(paris("2026-11-02T10:20"));

        moveTo(TicketStatus.PENDING);
        clock.advance(Duration.ofHours(1));
        moveTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getFirstRespondedAt()).isEqualTo(firstResponse);
        assertThat(service.view(ticket).firstResponseBreached()).isFalse();
    }

    @Test
    void lateFirstResponseIsReported() {
        clock.set(paris("2026-11-02T11:30"));
        assertThat(service.view(ticket).firstResponseBreached()).isTrue();
    }

    @Test
    void waitingPausesTheClockAndPushesTheDeadlineBackByTheBusinessTimePaused() {
        clock.set(paris("2026-11-02T12:00"));
        moveTo(TicketStatus.PENDING);
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.PAUSED);

        // Paused from Monday 12:00 to Tuesday 12:00: 6 h + 4 h = 600 business minutes.
        clock.set(paris("2026-11-03T12:00"));
        moveTo(TicketStatus.IN_PROGRESS);

        assertThat(ticket.getSlaPausedMinutes()).isEqualTo(600);
        assertThat(ticket.getSlaPausedAt()).isNull();
        assertThat(ticket.getResolutionDueAt()).isEqualTo(paris("2026-11-03T18:00"));
        assertThat(service.view(ticket).consumedPercent()).as("2 h worked of 8").isEqualTo(25);
    }

    @Test
    void warningAndBreachFollowTheConsumedTime() {
        clock.set(paris("2026-11-02T15:30"));
        assertThat(service.view(ticket).state()).as("5.5 h of 8 = 68 %").isEqualTo(SlaState.ON_TRACK);

        clock.set(paris("2026-11-02T16:00"));
        assertThat(service.view(ticket).state()).as("6 h of 8 = 75 %").isEqualTo(SlaState.AT_RISK);

        clock.set(paris("2026-11-02T18:01"));
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.BREACHED);
    }

    @Test
    void resolvedTicketsAreMetOrMissedAndTheClockStops() {
        clock.set(paris("2026-11-02T17:00"));
        ticket.setResolvedAt(clock.instant());
        moveTo(TicketStatus.RESOLVED);
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.MET);

        // Rejected the next morning at 09:00: paused Mon 17:00-18:00 + Tue 08:00-09:00 = 2 business hours (the
        // night does not count), so the 18:00 deadline moves to Tuesday 10:00.
        clock.set(paris("2026-11-03T09:00"));
        ticket.setResolvedAt(null);
        moveTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getSlaPausedMinutes()).isEqualTo(120);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(paris("2026-11-03T10:00"));

        clock.set(paris("2026-11-03T10:30"));
        ticket.setResolvedAt(clock.instant());
        moveTo(TicketStatus.RESOLVED);
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.MISSED);
    }

    @Test
    void criticalTicketsRunRoundTheClock() {
        Ticket critical = Ticket.builder().reference("INC-2026-0002").priority(priority(PriorityCode.CRITICAL)).build();
        ReflectionTestUtils.setField(critical, "createdAt", paris("2026-11-07T23:00"));

        service.onCreated(critical, critical.getCreatedAt());

        assertThat(critical.getFirstResponseDueAt()).isEqualTo(paris("2026-11-07T23:15"));
        assertThat(critical.getResolutionDueAt()).isEqualTo(paris("2026-11-08T01:00"));
    }

    @Test
    void priorityChangeRecomputesTheDeadlinesFromCreation() {
        ticket.setPriority(priority(PriorityCode.CRITICAL));
        service.onPriorityChanged(ticket);

        assertThat(ticket.getResolutionDueAt()).isEqualTo(paris("2026-11-02T12:00"));
        assertThat(ticket.getSlaBusinessHours()).isFalse();
    }

    @Test
    void rejectedTicketsHaveNoSla() {
        moveTo(TicketStatus.REJECTED);
        assertThat(service.view(ticket).state()).isEqualTo(SlaState.NONE);
    }
}
