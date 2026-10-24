package com.skycast.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Raw Open-Meteo JSON shapes (snake_case). Values are boxed because the API may return null. */
public final class OpenMeteoModels {

    private OpenMeteoModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(Double latitude, Double longitude, String timezone,
                           @JsonProperty("utc_offset_seconds") Integer utcOffsetSeconds,
                           Current current, Hourly hourly, Daily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
            String time,
            @JsonProperty("temperature_2m") Double temperature,
            @JsonProperty("relative_humidity_2m") Double humidity,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            @JsonProperty("precipitation") Double precipitation,
            @JsonProperty("weather_code") Integer weatherCode,
            @JsonProperty("wind_speed_10m") Double windSpeed,
            @JsonProperty("wind_direction_10m") Double windDirection,
            @JsonProperty("is_day") Integer isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability,
            @JsonProperty("weather_code") List<Integer> weatherCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum,
            @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geocoding(List<Place> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(String name, Double latitude, Double longitude, String country, String admin1) {
    }
}
