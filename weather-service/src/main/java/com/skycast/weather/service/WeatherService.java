package com.skycast.weather.service;

import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.dto.ForecastDto;
import com.skycast.common.dto.LocationDto;
import com.skycast.common.error.ApiException;
import com.skycast.weather.client.OpenMeteoClient;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.Location;
import com.skycast.weather.support.Geo;
import com.skycast.weather.support.WeatherMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Live weather with resilience: every Open-Meteo call runs inside a Resilience4j circuit breaker.
 * If the provider fails (or the breaker is open) current conditions fall back to the last stored snapshot.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final OpenMeteoClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final LocationService locations;
    private final SnapshotService snapshots;
    private final FetchAuditService audit;
    private final WeatherMapper mapper;
    private final MeterRegistry meters;

    public WeatherService(OpenMeteoClient client, CircuitBreakerFactory<?, ?> circuitBreakers, LocationService locations,
                          SnapshotService snapshots, FetchAuditService audit, WeatherMapper mapper, MeterRegistry meters) {
        this.client = client;
        this.circuitBreakers = circuitBreakers;
        this.locations = locations;
        this.snapshots = snapshots;
        this.audit = audit;
        this.mapper = mapper;
        this.meters = meters;
    }

    public CurrentWeatherDto current(double lat, double lon, String name) {
        Location location = locations.resolve(name, null, lat, lon);
        long started = System.nanoTime();
        OpenMeteoModels.Forecast live = circuitBreakers.create("openMeteo").run(
                () -> client.current(location.getLatitude(), location.getLongitude()),
                error -> {
                    log.warn("Open-Meteo current failed for {}: {}", location.getName(), error.toString());
                    audit.log("current", lat, lon, false, started, error.toString());
                    return null;
                });

        if (live != null && live.current() != null) {
            audit.log("current", lat, lon, true, started, null);
            snapshots.record(location.getId(), live.current());
            meters.counter("skycast.weather.requests", "type", "current", "source", "live").increment();
            return mapper.toCurrent(location, live.current(), "LIVE");
        }

        meters.counter("skycast.weather.requests", "type", "current", "source", "cache").increment();
        return snapshots.latest(location.getId())
                .map(s -> mapper.toCurrent(location, s, "CACHED"))
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Weather provider unavailable and no stored reading for " + location.getName()));
    }

    @Cacheable(cacheNames = "forecast", key = "T(com.skycast.weather.support.Geo).key(#lat, #lon)")
    public ForecastDto forecast(double lat, double lon) {
        double rlat = Geo.round2(lat);
        double rlon = Geo.round2(lon);
        long started = System.nanoTime();
        OpenMeteoModels.Forecast forecast = circuitBreakers.create("openMeteo").run(
                () -> client.forecast(rlat, rlon),
                error -> {
                    audit.log("forecast", rlat, rlon, false, started, error.toString());
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Forecast provider is unavailable, try again shortly");
                });
        audit.log("forecast", rlat, rlon, true, started, null);
        meters.counter("skycast.weather.requests", "type", "forecast", "source", "live").increment();
        return mapper.toForecast(rlat, rlon, forecast);
    }

    @Cacheable(cacheNames = "geocode", key = "#query.trim().toLowerCase()", unless = "#result.isEmpty()")
    public List<LocationDto> geocode(String query) {
        OpenMeteoModels.Geocoding result = circuitBreakers.create("openMeteo").run(
                () -> client.geocode(query.trim()),
                error -> null);
        if (result == null || result.results() == null) {
            return List.of();
        }
        return result.results().stream()
                .filter(p -> p.latitude() != null && p.longitude() != null)
                .map(p -> new LocationDto(p.name(), p.country(), p.admin1(), Geo.round2(p.latitude()), Geo.round2(p.longitude())))
                .toList();
    }
}
