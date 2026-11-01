package com.nexadesk.ticket;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Generates INC-2026-0001 / REQ-2026-0001 references: one counter per type and year.
 *
 * <p>The increment is a single atomic upsert. It runs in the caller's transaction, so the counter row stays
 * locked until the ticket is committed: concurrent creations are serialized and can never get the same
 * number. The very first ticket of a year (no row to lock yet) is covered by the ON CONFLICT clause.
 * The UNIQUE constraint on tickets.reference remains as a last safety net.
 */
@Component
public class TicketReferenceGenerator {

    private static final String NEXT_VALUE_SQL = """
            INSERT INTO ticket_reference_counters (type, year, last_value) VALUES (:type, :year, 1)
            ON CONFLICT (type, year) DO UPDATE SET last_value = ticket_reference_counters.last_value + 1
            RETURNING last_value""";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public TicketReferenceGenerator(JdbcClient jdbc, Clock clock, @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    public String next(TicketType type) {
        return next(type, LocalDate.now(clock.withZone(zone)).getYear());
    }

    /** Explicit year: used when importing or seeding tickets created in the past. */
    public String next(TicketType type, int year) {
        int value = jdbc.sql(NEXT_VALUE_SQL)
                .param("type", type.name())
                .param("year", year)
                .query(Integer.class)
                .single();
        return "%s-%d-%04d".formatted(type.referencePrefix(), year, value);
    }
}
