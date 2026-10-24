package com.skycast.common.dto;

import java.time.LocalDate;
import java.util.List;

/** source = DATABASE (all days already stored) or ARCHIVE (fetched from the Open-Meteo archive and stored). */
public record HistoryDto(double latitude, double longitude, LocalDate startDate, LocalDate endDate,
                         List<DailyPoint> days, HistoryStats stats, String source) {
}
