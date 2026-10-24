package com.skycast.weather.web;

import com.skycast.weather.domain.WeatherSnapshot;

import java.time.LocalDateTime;

public record SnapshotView(LocalDateTime observedAt, double temperature, int humidity, double windSpeed,
                           double precipitation, int weatherCode) {

    public static SnapshotView of(WeatherSnapshot s) {
        return new SnapshotView(s.getObservedAt(), s.getTemperature(), s.getHumidity(), s.getWindSpeed(),
                s.getPrecipitation(), s.getWeatherCode());
    }
}
