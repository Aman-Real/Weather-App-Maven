package com.skycast.weather.web;

import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.dto.ForecastDto;
import com.skycast.common.dto.HistoryDto;
import com.skycast.common.dto.LocationDto;
import com.skycast.common.dto.MapPointDto;
import com.skycast.weather.repository.WeatherStatsDao;
import com.skycast.weather.service.HistoryService;
import com.skycast.weather.service.LocationService;
import com.skycast.weather.service.MapService;
import com.skycast.weather.service.SnapshotService;
import com.skycast.weather.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Public weather REST API (validated request parameters, JSON responses). */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weather;
    private final HistoryService history;
    private final MapService map;
    private final LocationService locations;
    private final SnapshotService snapshots;
    private final WeatherStatsDao stats;

    public WeatherController(WeatherService weather, HistoryService history, MapService map,
                             LocationService locations, SnapshotService snapshots, WeatherStatsDao stats) {
        this.weather = weather;
        this.history = history;
        this.map = map;
        this.locations = locations;
        this.snapshots = snapshots;
        this.stats = stats;
    }

    @GetMapping("/current")
    public CurrentWeatherDto current(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                                     @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
                                     @RequestParam(required = false) @Size(max = 120) String name) {
        return weather.current(lat, lon, name);
    }

    @GetMapping("/forecast")
    public ForecastDto forecast(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                                @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon) {
        return weather.forecast(lat, lon);
    }

    @GetMapping("/history")
    public HistoryDto history(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                              @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                              @RequestParam(required = false) @Size(max = 120) String name) {
        return history.history(lat, lon, start, end, name);
    }

    @GetMapping("/geocode")
    public List<LocationDto> geocode(@RequestParam("q") @Size(min = 2, max = 80) String query) {
        return weather.geocode(query);
    }

    @GetMapping("/map")
    public List<MapPointDto> map(@RequestParam double north, @RequestParam double south,
                                 @RequestParam double east, @RequestParam double west) {
        double[] b = MapService.normalise(north, south, east, west);
        return map.grid(b[0], b[1], b[2], b[3]);
    }

    /** Criteria API search over stored locations. */
    @GetMapping("/locations")
    public List<LocationView> searchLocations(@RequestParam(required = false) String name,
                                              @RequestParam(required = false) String country,
                                              @RequestParam(required = false) Double minLat,
                                              @RequestParam(required = false) Double maxLat) {
        return locations.search(name, country, minLat, maxLat).stream().map(LocationView::of).toList();
    }

    /** HQL through the Hibernate Session. */
    @GetMapping("/locations/recent")
    public List<LocationView> recentLocations(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return locations.recentlyUpdated(limit).stream().map(LocationView::of).toList();
    }

    @GetMapping("/locations/{id}/snapshots")
    public List<SnapshotView> snapshots(@PathVariable Long id) {
        return snapshots.recent(id).stream().map(SnapshotView::of).toList();
    }

    /** Spring JDBC aggregate statistics; method-level security on top of the URL rule. */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WeatherStatsDao.LocationStats> stats(@RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        return stats.statsSince(LocalDateTime.now().minusHours(hours));
    }
}
