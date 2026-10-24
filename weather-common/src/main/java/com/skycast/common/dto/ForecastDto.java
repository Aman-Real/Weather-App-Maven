package com.skycast.common.dto;

import java.util.List;

public record ForecastDto(double latitude, double longitude, String timezone,
                          List<HourlyPoint> hourly, List<DailyPoint> daily) {
}
