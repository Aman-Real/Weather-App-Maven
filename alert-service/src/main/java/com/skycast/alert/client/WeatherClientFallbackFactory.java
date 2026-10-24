package com.skycast.alert.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** When weather-service is down (or the breaker is open) the evaluation for that location is skipped. */
@Component
public class WeatherClientFallbackFactory implements FallbackFactory<WeatherClient> {

    private static final Logger log = LoggerFactory.getLogger(WeatherClientFallbackFactory.class);

    @Override
    public WeatherClient create(Throwable cause) {
        return (lat, lon, name) -> {
            log.warn("weather-service unavailable for {} ({}): {}", name, lat + "," + lon, cause.toString());
            return null;
        };
    }
}
