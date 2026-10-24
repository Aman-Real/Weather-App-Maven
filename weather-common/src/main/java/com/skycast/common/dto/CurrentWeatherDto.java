package com.skycast.common.dto;

import java.time.LocalDateTime;

/** Current conditions. source = LIVE (fresh from Open-Meteo) or CACHED (last stored snapshot, circuit-breaker fallback). */
public record CurrentWeatherDto(
        String locationName,
        double latitude,
        double longitude,
        LocalDateTime time,
        double temperature,
        double apparentTemperature,
        int humidity,
        double windSpeed,
        double precipitation,
        int weatherCode,
        String condition,
        boolean day,
        String source) {
}
