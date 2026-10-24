package com.skycast.common.dto;

import java.time.LocalDate;

public record DailyPoint(LocalDate date, double tempMax, double tempMin, double precipitationSum,
                         double windSpeedMax, int weatherCode, String condition) {
}
