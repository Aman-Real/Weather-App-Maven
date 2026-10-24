package com.skycast.weather.service;

import com.skycast.common.dto.MapPointDto;
import com.skycast.weather.client.OpenMeteoClient;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.support.Geo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Temperature / wind grid for the interactive map: one multi-coordinate Open-Meteo request per map view. */
@Service
public class MapService {

    public static final int GRID_SIZE = 5;

    private final OpenMeteoClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public MapService(OpenMeteoClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreakers = circuitBreakers;
    }

    /** Bounds must already be normalised by {@link #normalise}; empty results are not cached. */
    @Cacheable(cacheNames = "grid", unless = "#result.isEmpty()")
    public List<MapPointDto> grid(double north, double south, double east, double west) {
        List<double[]> points = new ArrayList<>();
        double latStep = (north - south) / GRID_SIZE;
        double lonStep = (east - west) / GRID_SIZE;
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                points.add(new double[]{Geo.round2(south + (i + 0.5) * latStep), Geo.round2(west + (j + 0.5) * lonStep)});
            }
        }
        List<OpenMeteoModels.Forecast> results = circuitBreakers.create("openMeteoGrid")
                .run(() -> client.currentGrid(points), error -> List.of());
        List<MapPointDto> dtos = new ArrayList<>();
        for (int k = 0; k < results.size() && k < points.size(); k++) {
            OpenMeteoModels.Current c = results.get(k).current();
            if (c == null || c.temperature() == null) {
                continue;
            }
            dtos.add(new MapPointDto(points.get(k)[0], points.get(k)[1], c.temperature(),
                    c.windSpeed() == null ? 0 : c.windSpeed(),
                    c.windDirection() == null ? 0 : (int) Math.round(c.windDirection()),
                    c.weatherCode() == null ? 0 : c.weatherCode()));
        }
        return dtos;
    }

    /** Clamp to the map, cap the span at 30 degrees and snap to 0.5 degrees -> [north, south, east, west]. */
    public static double[] normalise(double north, double south, double east, double west) {
        double n = Geo.clamp(Math.max(north, south), -85, 85);
        double s = Geo.clamp(Math.min(north, south), -85, 85);
        double e = Geo.clamp(Math.max(east, west), -180, 180);
        double w = Geo.clamp(Math.min(east, west), -180, 180);
        double centerLat = (n + s) / 2;
        double centerLon = (e + w) / 2;
        double halfLat = Geo.clamp((n - s) / 2, 0.5, 15);
        double halfLon = Geo.clamp((e - w) / 2, 0.5, 15);
        return new double[]{
                Geo.snapHalf(Geo.clamp(centerLat + halfLat, -85, 85)),
                Geo.snapHalf(Geo.clamp(centerLat - halfLat, -85, 85)),
                Geo.snapHalf(Geo.clamp(centerLon + halfLon, -180, 180)),
                Geo.snapHalf(Geo.clamp(centerLon - halfLon, -180, 180))};
    }
}
