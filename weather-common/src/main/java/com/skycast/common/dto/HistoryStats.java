package com.skycast.common.dto;

import java.time.LocalDate;

public record HistoryStats(double averageMax, double averageMin, double totalPrecipitation,
                           LocalDate hottestDay, LocalDate wettestDay) {
}
