package com.nexadesk.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardTrendTest {

    @Test
    void trendIsAPercentageOfThePreviousPeriod() {
        assertThat(DashboardService.change(15, 10)).isEqualTo(50.0);
        assertThat(DashboardService.change(5, 10)).isEqualTo(-50.0);
        assertThat(DashboardService.change(7, 3)).isEqualTo(133.3);
        assertThat(DashboardService.change(4, 0)).as("no baseline to compare with").isNull();
    }
}
