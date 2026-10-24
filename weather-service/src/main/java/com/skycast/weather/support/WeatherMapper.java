package com.skycast.weather.support;

import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.dto.DailyPoint;
import com.skycast.common.dto.ForecastDto;
import com.skycast.common.dto.HourlyPoint;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.DailyWeather;
import com.skycast.weather.domain.Location;
import com.skycast.weather.domain.WeatherSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Converts Open-Meteo JSON and JPA entities into the shared DTOs. */
@Component
public class WeatherMapper {

    private static final int HOURS_AHEAD = 48;

    private final WeatherCodeTranslator translator;

    public WeatherMapper(WeatherCodeTranslator translator) {
        this.translator = translator;
    }

    public CurrentWeatherDto toCurrent(Location location, OpenMeteoModels.Current c, String source) {
        int code = intOr(c.weatherCode(), 0);
        return new CurrentWeatherDto(location.getName(), location.getLatitude(), location.getLongitude(),
                c.time() == null ? LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) : LocalDateTime.parse(c.time()),
                or(c.temperature()), or(c.apparentTemperature()), (int) Math.round(or(c.humidity())),
                or(c.windSpeed()), or(c.precipitation()), code, translator.describe(code),
                intOr(c.isDay(), 1) == 1, source);
    }

    public CurrentWeatherDto toCurrent(Location location, WeatherSnapshot s, String source) {
        return new CurrentWeatherDto(location.getName(), location.getLatitude(), location.getLongitude(),
                s.getObservedAt(), s.getTemperature(), s.getApparentTemperature(), s.getHumidity(),
                s.getWindSpeed(), s.getPrecipitation(), s.getWeatherCode(), translator.describe(s.getWeatherCode()),
                s.isDay(), source);
    }

    public ForecastDto toForecast(double lat, double lon, OpenMeteoModels.Forecast f) {
        LocalDateTime nowLocal = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(intOr(f.utcOffsetSeconds(), 0))
                .truncatedTo(ChronoUnit.HOURS);
        List<HourlyPoint> hourly = new ArrayList<>();
        OpenMeteoModels.Hourly h = f.hourly();
        if (h != null && h.time() != null) {
            for (int i = 0; i < h.time().size() && hourly.size() < HOURS_AHEAD; i++) {
                LocalDateTime time = LocalDateTime.parse(h.time().get(i));
                Double temp = at(h.temperature(), i);
                if (time.isBefore(nowLocal) || temp == null) {
                    continue;
                }
                Integer pop = at(h.precipitationProbability(), i);
                Integer code = at(h.weatherCode(), i);
                hourly.add(new HourlyPoint(time, temp, pop == null ? 0 : pop, code == null ? 0 : code));
            }
        }
        return new ForecastDto(lat, lon, f.timezone(), hourly, toDaily(f.daily()));
    }

    /** Days with missing values (the archive lags a few days) are skipped. */
    public List<DailyPoint> toDaily(OpenMeteoModels.Daily d) {
        List<DailyPoint> days = new ArrayList<>();
        if (d == null || d.time() == null) {
            return days;
        }
        for (int i = 0; i < d.time().size(); i++) {
            Double max = at(d.temperatureMax(), i);
            Double min = at(d.temperatureMin(), i);
            if (max == null || min == null) {
                continue;
            }
            int code = intOr(at(d.weatherCode(), i), 0);
            days.add(new DailyPoint(LocalDate.parse(d.time().get(i)), max, min,
                    or(at(d.precipitationSum(), i)), or(at(d.windSpeedMax(), i)), code, translator.describe(code)));
        }
        return days;
    }

    public DailyPoint toDaily(DailyWeather w) {
        return new DailyPoint(w.getDate(), w.getTempMax(), w.getTempMin(), w.getPrecipitationSum(),
                w.getWindSpeedMax(), w.getWeatherCode(), translator.describe(w.getWeatherCode()));
    }

    private static <T> T at(List<T> list, int i) {
        return list == null || i >= list.size() ? null : list.get(i);
    }

    private static double or(Double v) {
        return v == null ? 0.0 : v;
    }

    private static int intOr(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
