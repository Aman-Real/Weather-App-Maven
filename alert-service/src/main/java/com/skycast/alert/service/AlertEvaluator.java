package com.skycast.alert.service;

import com.skycast.alert.domain.AlertRule;
import com.skycast.alert.domain.Severity;
import com.skycast.common.dto.CurrentWeatherDto;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/** Pure decision logic (no I/O), so it is trivially unit-testable. */
@Component
public class AlertEvaluator {

    /** Beaufort 8 (gale) and WMO thunderstorm codes always produce a SEVERE alert. */
    static final double GALE_KMH = 62.0;
    static final int THUNDERSTORM_CODE = 95;

    public record Evaluation(String type, Severity severity, String message, double observed, double threshold) {
    }

    public Optional<Evaluation> evaluate(AlertRule rule, CurrentWeatherDto w) {
        double threshold = rule.getThreshold();
        String place = w.locationName();
        return switch (rule.getMetric()) {
            case TEMPERATURE_ABOVE -> w.temperature() > threshold
                    ? Optional.of(new Evaluation("TEMPERATURE_ABOVE", severity(w.temperature() - threshold, 5),
                    format("Temperature %.1f °C is above your limit of %.1f °C in %s", w.temperature(), threshold, place),
                    w.temperature(), threshold))
                    : Optional.empty();
            case TEMPERATURE_BELOW -> w.temperature() < threshold
                    ? Optional.of(new Evaluation("TEMPERATURE_BELOW", severity(threshold - w.temperature(), 5),
                    format("Temperature %.1f °C is below your limit of %.1f °C in %s", w.temperature(), threshold, place),
                    w.temperature(), threshold))
                    : Optional.empty();
            case WIND_ABOVE -> w.windSpeed() > threshold
                    ? Optional.of(new Evaluation("WIND_ABOVE", w.windSpeed() >= GALE_KMH ? Severity.SEVERE : Severity.WARNING,
                    format("Wind %.1f km/h is above your limit of %.1f km/h in %s", w.windSpeed(), threshold, place),
                    w.windSpeed(), threshold))
                    : Optional.empty();
            case PRECIPITATION_ABOVE -> w.precipitation() > threshold
                    ? Optional.of(new Evaluation("PRECIPITATION_ABOVE", severity(w.precipitation() - threshold, 10),
                    format("Precipitation %.1f mm is above your limit of %.1f mm in %s", w.precipitation(), threshold, place),
                    w.precipitation(), threshold))
                    : Optional.empty();
        };
    }

    /** Significant weather that every subscriber of the location should hear about, whatever their rule. */
    public Optional<Evaluation> severeWeather(CurrentWeatherDto w) {
        if (w.weatherCode() >= THUNDERSTORM_CODE) {
            return Optional.of(new Evaluation("SEVERE_WEATHER", Severity.SEVERE,
                    format("%s reported in %s", w.condition(), w.locationName()), w.weatherCode(), THUNDERSTORM_CODE));
        }
        if (w.windSpeed() >= GALE_KMH) {
            return Optional.of(new Evaluation("SEVERE_WEATHER", Severity.SEVERE,
                    format("Gale-force wind %.1f km/h in %s", w.windSpeed(), w.locationName()), w.windSpeed(), GALE_KMH));
        }
        return Optional.empty();
    }

    private static Severity severity(double excess, double severeFrom) {
        return excess >= severeFrom ? Severity.SEVERE : Severity.WARNING;
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
