package com.nexadesk.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.reporting.AnalyticsQueries.Bucket;
import com.nexadesk.reporting.AnalyticsQueries.Granularity;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelinesTest {

    @Test
    void missingDaysBecomeZerosSoChartsHaveNoGaps() {
        List<TimelinePoint> points = Timelines.merge(
                List.of(new Bucket(LocalDate.of(2026, 11, 2), 3), new Bucket(LocalDate.of(2026, 11, 4), 1)),
                List.of(new Bucket(LocalDate.of(2026, 11, 3), 2)),
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), Granularity.DAY);

        assertThat(points).extracting(TimelinePoint::created).containsExactly(3L, 0L, 1L, 0L);
        assertThat(points).extracting(TimelinePoint::resolved).containsExactly(0L, 2L, 0L, 0L);
    }

    @Test
    void weeksStartOnMondayLikePostgresDateTrunc() {
        List<TimelinePoint> points = Timelines.merge(List.of(), List.of(),
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 18), Granularity.WEEK);

        assertThat(points).extracting(TimelinePoint::bucket).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
    }

    @Test
    void monthsStartOnTheFirst() {
        List<TimelinePoint> points = Timelines.merge(List.of(new Bucket(LocalDate.of(2026, 10, 1), 5)), List.of(),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 11, 2), Granularity.MONTH);

        assertThat(points).extracting(TimelinePoint::bucket).containsExactly(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        assertThat(points.get(1).created()).isEqualTo(5);
    }
}
