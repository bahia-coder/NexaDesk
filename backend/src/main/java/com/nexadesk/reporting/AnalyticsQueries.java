package com.nexadesk.reporting;

import com.nexadesk.reporting.dto.AnalyticsDtos.ActivityItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.CountItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.Satisfaction;
import com.nexadesk.reporting.dto.AnalyticsDtos.SlaCompliance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TechnicianPerformance;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries behind the dashboards and reports, computed by PostgreSQL from the live data.
 *
 * <p>Definitions used everywhere (one place, so every screen shows the same numbers):
 * <ul>
 *   <li>open = NEW, ASSIGNED, IN_PROGRESS or PENDING;</li>
 *   <li>overdue = clock running and deadline passed, or put on hold after the deadline;</li>
 *   <li>resolution time = resolved_at − created_at, in calendar hours;</li>
 *   <li>a ticket counts as resolved in a period when its resolved_at falls in it.</li>
 * </ul>
 * Optional filters ({@link Scope#categoryId}, {@link Scope#technicianId}) apply to the tickets; null means "all".
 */
@Repository
public class AnalyticsQueries {

    /** Period [from, to[ plus optional ticket filters. */
    public record Scope(Instant from, Instant to, Long categoryId, Long technicianId) {

        public static Scope of(Instant from, Instant to) {
            return new Scope(from, to, null, null);
        }
    }

    public enum Granularity { DAY, WEEK, MONTH }

    public record Bucket(LocalDate bucket, long count) {
    }

    /**
     * Appended to other SQL: the leading space is explicit because a text block strips the common indentation
     * (it would otherwise produce "...where trueand (...").
     */
    private static final String TICKET_FILTERS =
            " and (cast(:categoryId as bigint) is null or t.category_id = :categoryId)"
            + " and (cast(:technicianId as bigint) is null or t.assignee_id = :technicianId)";

    private static final String OPEN = "t.status in ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING')";
    private static final String OVERDUE = """
            ((t.status in ('NEW', 'ASSIGNED', 'IN_PROGRESS') and t.resolution_due_at < :now)
             or (t.status = 'PENDING' and t.sla_paused_at > t.resolution_due_at))""";
    private static final String AT_RISK = """
            (t.status in ('NEW', 'ASSIGNED', 'IN_PROGRESS') and t.resolution_due_at >= :now
             and t.sla_warning_sent_at is not null)""";

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public AnalyticsQueries(JdbcClient jdbc, @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.jdbc = jdbc;
        this.zone = zone;
    }

    // ---- Period counts ----------------------------------------------------------------------------------

    public long createdCount(Scope s) {
        return scoped("select count(*) from tickets t where t.created_at >= :from and t.created_at < :to" + TICKET_FILTERS, s)
                .query(Long.class).single();
    }

    public long resolvedCount(Scope s) {
        return scoped("select count(*) from tickets t where t.resolved_at >= :from and t.resolved_at < :to" + TICKET_FILTERS, s)
                .query(Long.class).single();
    }

    /** Tickets created in the period that are now resolved or closed (numerator of the resolution rate). */
    public long resolvedAmongCreated(Scope s) {
        return scoped("""
                select count(*) from tickets t
                where t.created_at >= :from and t.created_at < :to and t.status in ('RESOLVED', 'CLOSED')"""
                + TICKET_FILTERS, s).query(Long.class).single();
    }

    /** Average resolution time (hours) of the tickets resolved in the period; null when none was resolved. */
    public Double avgResolutionHours(Scope s) {
        // AVG over no row is SQL NULL: optional() accepts it, single() would reject it.
        return scoped("""
                select avg(extract(epoch from t.resolved_at - t.created_at)) / 3600 from tickets t
                where t.resolved_at >= :from and t.resolved_at < :to""" + TICKET_FILTERS, s)
                .query((rs, i) -> nullableDouble(rs, 1)).optional().orElse(null);
    }

    // ---- Current state ----------------------------------------------------------------------------------

    public record CurrentState(long open, long inProgress, long pending, long overdue, long atRisk, long unassigned) {
    }

    public CurrentState currentState(Long categoryId, Long technicianId, Instant now) {
        return jdbc.sql("""
                        select count(*) filter (where %s) as open,
                               count(*) filter (where t.status = 'IN_PROGRESS') as in_progress,
                               count(*) filter (where t.status = 'PENDING') as pending,
                               count(*) filter (where %s) as overdue,
                               count(*) filter (where %s) as at_risk,
                               count(*) filter (where t.status = 'NEW' and t.assignee_id is null) as unassigned
                        from tickets t where true""".formatted(OPEN, OVERDUE, AT_RISK) + TICKET_FILTERS)
                .param("categoryId", categoryId).param("technicianId", technicianId).param("now", utc(now))
                .query((rs, i) -> new CurrentState(rs.getLong("open"), rs.getLong("in_progress"),
                        rs.getLong("pending"), rs.getLong("overdue"), rs.getLong("at_risk"), rs.getLong("unassigned")))
                .single();
    }

    /** Every status, with zero counts included, in workflow order. */
    public List<CountItem> byStatus(Long categoryId, Long technicianId) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.sql("select t.status, count(*) from tickets t where true" + TICKET_FILTERS + " group by t.status")
                .param("categoryId", categoryId).param("technicianId", technicianId)
                .query(rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        List<CountItem> items = new ArrayList<>();
        for (String status : List.of("NEW", "ASSIGNED", "IN_PROGRESS", "PENDING", "RESOLVED", "CLOSED", "REJECTED")) {
            items.add(new CountItem(status, status, null, counts.getOrDefault(status, 0L)));
        }
        return items;
    }

    // ---- Breakdowns of the tickets created in the period ------------------------------------------------

    public List<CountItem> createdByPriority(Scope s) {
        return scoped("""
                select p.code, p.label, p.color, count(t.id) as n
                from priorities p
                left join tickets t on t.priority_id = p.id and t.created_at >= :from and t.created_at < :to"""
                + TICKET_FILTERS + " group by p.code, p.label, p.color, p.rank order by p.rank", s)
                .query((rs, i) -> new CountItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)))
                .list();
    }

    public List<CountItem> createdByCategory(Scope s) {
        return scoped("""
                select c.id, c.name, count(t.id) as n
                from ticket_categories c
                left join tickets t on t.category_id = c.id and t.created_at >= :from and t.created_at < :to"""
                + TICKET_FILTERS + " group by c.id, c.name having count(t.id) > 0 or bool_or(c.active)"
                + " order by n desc, c.name", s)
                .query((rs, i) -> new CountItem(String.valueOf(rs.getLong(1)), rs.getString(2), null, rs.getLong(3)))
                .list();
    }

    /** Created or resolved tickets per bucket; buckets without tickets are absent (filled by the caller). */
    public List<Bucket> createdTimeline(Scope s, Granularity g) {
        return timeline("created_at", s, g);
    }

    public List<Bucket> resolvedTimeline(Scope s, Granularity g) {
        return timeline("resolved_at", s, g);
    }

    private List<Bucket> timeline(String column, Scope s, Granularity g) {
        // column is one of two constants above, never user input.
        return scoped("""
                select date_trunc(:unit, t.%1$s at time zone :zone)::date as bucket, count(*)
                from tickets t where t.%1$s >= :from and t.%1$s < :to""".formatted(column) + TICKET_FILTERS
                + " group by 1 order by 1", s)
                .param("unit", g.name().toLowerCase()).param("zone", zone.getId())
                .query((rs, i) -> new Bucket(rs.getObject(1, LocalDate.class), rs.getLong(2))).list();
    }

    // ---- Quality ----------------------------------------------------------------------------------------

    public SlaCompliance slaCompliance(Scope s) {
        return scoped("""
                select
                  100.0 * avg(case when t.first_responded_at <= t.first_response_due_at then 1 else 0 end)
                    filter (where t.created_at >= :from and t.created_at < :to and t.first_responded_at is not null
                            and t.first_response_due_at is not null) as first_response,
                  100.0 * avg(case when t.resolved_at <= t.resolution_due_at then 1 else 0 end)
                    filter (where t.resolved_at >= :from and t.resolved_at < :to and t.resolution_due_at is not null)
                    as resolution,
                  count(*) filter (where t.resolved_at >= :from and t.resolved_at < :to
                                   and t.resolution_due_at is not null) as measured
                from tickets t where true""" + TICKET_FILTERS, s)
                .query((rs, i) -> new SlaCompliance(nullableDouble(rs, 1), nullableDouble(rs, 2), rs.getLong(3)))
                .single();
    }

    public Satisfaction satisfaction(Scope s) {
        long[] distribution = new long[5];
        scoped("""
                select f.rating, count(*) from ticket_feedback f join tickets t on t.id = f.ticket_id
                where f.created_at >= :from and f.created_at < :to""" + TICKET_FILTERS + " group by f.rating", s)
                .query(rs -> {
                    distribution[rs.getInt(1) - 1] = rs.getLong(2);
                });
        long count = 0;
        long sum = 0;
        for (int rating = 1; rating <= 5; rating++) {
            count += distribution[rating - 1];
            sum += rating * distribution[rating - 1];
        }
        Double average = count == 0 ? null : (double) sum / count;
        return new Satisfaction(average, count, List.of(distribution[0], distribution[1], distribution[2],
                distribution[3], distribution[4]));
    }

    /** One row per active technician (or only the filtered one), best performers first. */
    public List<TechnicianPerformance> technicianPerformance(Scope s) {
        String ticketScope = " and (cast(:categoryId as bigint) is null or t.category_id = :categoryId)";
        return jdbc.sql("""
                        select u.id, u.first_name || ' ' || u.last_name as full_name, u.avatar_color,
                          (select count(*) from tickets t where t.assignee_id = u.id
                             and t.status in ('ASSIGNED', 'IN_PROGRESS', 'PENDING')%1$s) as open_assigned,
                          (select count(*) from tickets t where t.assignee_id = u.id
                             and t.resolved_at >= :from and t.resolved_at < :to%1$s) as resolved,
                          (select avg(extract(epoch from t.resolved_at - t.created_at)) / 3600 from tickets t
                             where t.assignee_id = u.id and t.resolved_at >= :from and t.resolved_at < :to%1$s)
                             as avg_hours,
                          (select 100.0 * avg(case when t.resolved_at <= t.resolution_due_at then 1 else 0 end)
                             from tickets t where t.assignee_id = u.id and t.resolved_at >= :from
                             and t.resolved_at < :to and t.resolution_due_at is not null%1$s) as sla_rate,
                          (select avg(f.rating) from ticket_feedback f join tickets t on t.id = f.ticket_id
                             where t.assignee_id = u.id and f.created_at >= :from and f.created_at < :to%1$s)
                             as avg_rating,
                          (select coalesce(sum(i.time_spent_minutes), 0) from interventions i
                             where i.technician_id = u.id and i.created_at >= :from and i.created_at < :to)
                             as minutes
                        from users u join roles r on r.id = u.role_id
                        where r.code = 'TECHNICIAN' and u.active
                          and (cast(:technicianId as bigint) is null or u.id = :technicianId)
                        order by resolved desc, full_name""".formatted(ticketScope))
                .param("from", utc(s.from())).param("to", utc(s.to()))
                .param("categoryId", s.categoryId()).param("technicianId", s.technicianId())
                .query((rs, i) -> new TechnicianPerformance(rs.getLong("id"), rs.getString("full_name"),
                        rs.getString("avatar_color"), rs.getLong("open_assigned"), rs.getLong("resolved"),
                        nullableDouble(rs, "avg_hours"), nullableDouble(rs, "sla_rate"),
                        nullableDouble(rs, "avg_rating"), rs.getLong("minutes")))
                .list();
    }

    /** Latest timeline entries, optionally limited to the tickets assigned to one technician. */
    public List<ActivityItem> recentActivity(int limit, Long assigneeId) {
        return jdbc.sql("""
                        select h.created_at, h.action, t.id, t.reference,
                               coalesce(u.first_name || ' ' || u.last_name, 'Système') as actor,
                               h.field, h.old_value, h.new_value, h.comment
                        from ticket_history h
                        join tickets t on t.id = h.ticket_id
                        left join users u on u.id = h.actor_id
                        where (cast(:assigneeId as bigint) is null or t.assignee_id = :assigneeId)
                        order by h.created_at desc, h.id desc
                        limit :limit""")
                .param("assigneeId", assigneeId).param("limit", limit)
                .query((rs, i) -> new ActivityItem(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getLong(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9)))
                .list();
    }

    // ---- Technician-specific ----------------------------------------------------------------------------

    public long minutesLogged(Long technicianId, Instant from, Instant to) {
        return jdbc.sql("""
                        select coalesce(sum(i.time_spent_minutes), 0) from interventions i
                        where i.technician_id = :id and i.created_at >= :from and i.created_at < :to""")
                .param("id", technicianId).param("from", utc(from)).param("to", utc(to))
                .query(Long.class).single();
    }

    /** Open tickets of my categories nobody has taken yet. */
    public long unassignedInSpecialties(Long technicianId) {
        return jdbc.sql("""
                        select count(*) from tickets t
                        join technician_specialties s on s.category_id = t.category_id and s.user_id = :id
                        where t.status = 'NEW' and t.assignee_id is null""")
                .param("id", technicianId).query(Long.class).single();
    }

    /** Assigned open tickets that need attention first: high / critical priority, at risk or overdue. */
    public long urgentAssigned(Long technicianId, Instant now) {
        return jdbc.sql("""
                        select count(*) from tickets t join priorities p on p.id = t.priority_id
                        where t.assignee_id = :id and t.status in ('ASSIGNED', 'IN_PROGRESS', 'PENDING')
                          and (p.code in ('HIGH', 'CRITICAL') or %s or %s)""".formatted(OVERDUE, AT_RISK))
                .param("id", technicianId).param("now", utc(now)).query(Long.class).single();
    }

    // ---- Employee-specific ------------------------------------------------------------------------------

    public record RequesterCounts(long waiting, long inProgress, long resolved, long toConfirm) {
    }

    public RequesterCounts requesterCounts(Long requesterId) {
        return jdbc.sql("""
                        select count(*) filter (where t.status in ('NEW', 'ASSIGNED')),
                               count(*) filter (where t.status in ('IN_PROGRESS', 'PENDING')),
                               count(*) filter (where t.status in ('RESOLVED', 'CLOSED')),
                               count(*) filter (where t.status = 'RESOLVED')
                        from tickets t where t.requester_id = :id""")
                .param("id", requesterId)
                .query((rs, i) -> new RequesterCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
                .single();
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    private JdbcClient.StatementSpec scoped(String sql, Scope s) {
        return jdbc.sql(sql)
                .param("from", utc(s.from())).param("to", utc(s.to()))
                .param("categoryId", s.categoryId()).param("technicianId", s.technicianId());
    }

    /** The PostgreSQL driver binds OffsetDateTime (not Instant) to timestamptz. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Double nullableDouble(ResultSet rs, int column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }
}
