package com.skycast.weather.service;

import com.skycast.common.dto.DailyPoint;
import com.skycast.common.dto.HistoryDto;
import com.skycast.common.dto.HistoryStats;
import com.skycast.common.error.ApiException;
import com.skycast.weather.client.OpenMeteoClient;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.DailyWeather;
import com.skycast.weather.domain.Location;
import com.skycast.weather.repository.DailyWeatherRepository;
import com.skycast.weather.support.WeatherMapper;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Past weather: served from the daily_weather table, topped up from the Open-Meteo archive when days are missing. */
@Service
public class HistoryService {

    public static final int MAX_DAYS = 366;

    private final DailyWeatherRepository daily;
    private final LocationService locations;
    private final OpenMeteoClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final FetchAuditService audit;
    private final WeatherMapper mapper;

    public HistoryService(DailyWeatherRepository daily, LocationService locations, OpenMeteoClient client,
                          CircuitBreakerFactory<?, ?> circuitBreakers, FetchAuditService audit, WeatherMapper mapper) {
        this.daily = daily;
        this.locations = locations;
        this.client = client;
        this.circuitBreakers = circuitBreakers;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public HistoryDto history(double lat, double lon, LocalDate start, LocalDate end, String name) {
        validate(start, end);
        Location location = locations.resolve(name, null, lat, lon);
        List<DailyWeather> stored = daily.findRange(location.getId(), start, end);
        long expectedDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (stored.size() >= expectedDays) {
            return build(location, start, end, stored, "DATABASE");
        }

        long started = System.nanoTime();
        OpenMeteoModels.Forecast archive = circuitBreakers.create("openMeteoArchive").run(
                () -> client.archive(location.getLatitude(), location.getLongitude(), start, end),
                error -> null);
        if (archive == null || archive.daily() == null) {
            audit.log("archive", lat, lon, false, started, "archive unavailable"); // survives a rollback (REQUIRES_NEW)
            if (stored.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Historical data provider is unavailable");
            }
            return build(location, start, end, stored, "PARTIAL");
        }
        audit.log("archive", lat, lon, true, started, null);

        List<DailyWeather> merged = merge(location, archive.daily(), stored, start, end);
        daily.saveAll(merged);
        return build(location, start, end, merged, "ARCHIVE");
    }

    /** Creates or updates one DailyWeather per archive day inside [start, end]; shared with the Spring Batch job. */
    public List<DailyWeather> merge(Location location, OpenMeteoModels.Daily archiveDays, List<DailyWeather> existing,
                                    LocalDate start, LocalDate end) {
        Map<LocalDate, DailyWeather> byDate = existing.stream()
                .collect(Collectors.toMap(DailyWeather::getDate, Function.identity(), (a, b) -> a));
        for (DailyPoint p : mapper.toDaily(archiveDays)) {
            if (p.date().isBefore(start) || p.date().isAfter(end)) {
                continue;
            }
            DailyWeather row = byDate.computeIfAbsent(p.date(), d -> new DailyWeather(location, d));
            row.update(p.tempMax(), p.tempMin(), p.precipitationSum(), p.windSpeedMax(), p.weatherCode());
        }
        return byDate.values().stream().sorted(Comparator.comparing(DailyWeather::getDate)).toList();
    }

    private HistoryDto build(Location location, LocalDate start, LocalDate end, List<DailyWeather> rows, String source) {
        List<DailyPoint> days = rows.stream()
                .filter(r -> !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                .sorted(Comparator.comparing(DailyWeather::getDate))
                .map(mapper::toDaily)
                .toList();
        return new HistoryDto(location.getLatitude(), location.getLongitude(), start, end, days, stats(days), source);
    }

    static HistoryStats stats(List<DailyPoint> days) {
        if (days.isEmpty()) {
            return new HistoryStats(0, 0, 0, null, null);
        }
        double avgMax = days.stream().mapToDouble(DailyPoint::tempMax).average().orElse(0);
        double avgMin = days.stream().mapToDouble(DailyPoint::tempMin).average().orElse(0);
        double rain = days.stream().mapToDouble(DailyPoint::precipitationSum).sum();
        LocalDate hottest = days.stream().max(Comparator.comparingDouble(DailyPoint::tempMax)).map(DailyPoint::date).orElse(null);
        LocalDate wettest = days.stream().max(Comparator.comparingDouble(DailyPoint::precipitationSum)).map(DailyPoint::date).orElse(null);
        return new HistoryStats(round(avgMax), round(avgMin), round(rain), hottest, wettest);
    }

    private static void validate(LocalDate start, LocalDate end) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("start must be on or before end");
        }
        if (end.isAfter(yesterday)) {
            throw ApiException.badRequest("end must be yesterday or earlier (" + yesterday + ")");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("at most " + MAX_DAYS + " days per request");
        }
        if (start.isBefore(LocalDate.of(1940, 1, 1))) {
            throw ApiException.badRequest("archive starts in 1940");
        }
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
