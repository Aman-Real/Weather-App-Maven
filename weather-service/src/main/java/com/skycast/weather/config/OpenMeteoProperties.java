package com.skycast.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** skycast.open-meteo.* — free, key-less weather API endpoints. */
@ConfigurationProperties(prefix = "skycast.open-meteo")
public record OpenMeteoProperties(
        @DefaultValue("https://api.open-meteo.com/v1/forecast") String forecastUrl,
        @DefaultValue("https://archive-api.open-meteo.com/v1/archive") String archiveUrl,
        @DefaultValue("https://geocoding-api.open-meteo.com/v1/search") String geocodingUrl) {
}
