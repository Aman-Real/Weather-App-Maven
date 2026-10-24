package com.skycast.weather.service;

import com.skycast.common.dto.DailyPoint;
import com.skycast.common.dto.HistoryStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryAndMapLogicTest {

    @Test
    void statsFindHottestAndWettestDays() {
        List<DailyPoint> days = List.of(
                new DailyPoint(LocalDate.of(2026, 9, 1), 27.0, 18.0, 12.4, 11.0, 63, "Moderate rain"),
                new DailyPoint(LocalDate.of(2026, 9, 2), 29.0, 19.0, 0.0, 8.0, 1, "Mainly clear"),
                new DailyPoint(LocalDate.of(2026, 9, 3), 28.0, 20.0, 3.1, 9.0, 61, "Slight rain"));

        HistoryStats stats = HistoryService.stats(days);

        assertThat(stats.averageMax()).isEqualTo(28.0);
        assertThat(stats.averageMin()).isEqualTo(19.0);
        assertThat(stats.totalPrecipitation()).isEqualTo(15.5);
        assertThat(stats.hottestDay()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(stats.wettestDay()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void emptyHistoryHasZeroStats() {
        assertThat(HistoryService.stats(List.of()).hottestDay()).isNull();
    }

    @Test
    void mapBoundsAreClampedCappedAndSnapped() {
        double[] b = MapService.normalise(31.27, 29.13, 79.41, 76.66);
        assertThat(b).containsExactly(31.5, 29.0, 79.5, 76.5);

        double[] world = MapService.normalise(89, -89, 200, -200);
        assertThat(world[0] - world[1]).isLessThanOrEqualTo(30.0);
        assertThat(world[2] - world[3]).isLessThanOrEqualTo(30.0);
    }
}
