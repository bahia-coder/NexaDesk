package com.nexadesk.reporting;

import com.nexadesk.reporting.AnalyticsQueries.Bucket;
import com.nexadesk.reporting.AnalyticsQueries.Granularity;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Merges created / resolved buckets into a continuous series (empty buckets become zeros, not gaps). */
public final class Timelines {

    private Timelines() {
    }

    public static List<TimelinePoint> merge(List<Bucket> created, List<Bucket> resolved, LocalDate from,
                                            LocalDate toInclusive, Granularity granularity) {
        Map<LocalDate, Long> createdByBucket = toMap(created);
        Map<LocalDate, Long> resolvedByBucket = toMap(resolved);
        List<TimelinePoint> points = new ArrayList<>();
        for (LocalDate bucket = start(from, granularity); !bucket.isAfter(toInclusive); bucket = next(bucket, granularity)) {
            points.add(new TimelinePoint(bucket, createdByBucket.getOrDefault(bucket, 0L),
                    resolvedByBucket.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    /** Same truncation as PostgreSQL's date_trunc: weeks start on Monday, months on the 1st. */
    static LocalDate start(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate bucket, Granularity granularity) {
        return switch (granularity) {
            case DAY -> bucket.plusDays(1);
            case WEEK -> bucket.plusWeeks(1);
            case MONTH -> bucket.plusMonths(1);
        };
    }

    private static Map<LocalDate, Long> toMap(List<Bucket> buckets) {
        Map<LocalDate, Long> map = new HashMap<>();
        buckets.forEach(b -> map.put(b.bucket(), b.count()));
        return map;
    }
}
