package com.skycast.alert;

import com.skycast.alert.domain.AlertRule;
import com.skycast.alert.domain.Metric;
import com.skycast.alert.domain.Severity;
import com.skycast.alert.service.AlertEvaluator;
import com.skycast.common.dto.CurrentWeatherDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEvaluatorTest {

    private final AlertEvaluator evaluator = new AlertEvaluator();

    private static CurrentWeatherDto weather(double temp, double wind, double rain, int code) {
        return new CurrentWeatherDto("Dehradun", 30.32, 78.03, LocalDateTime.now(), temp, temp, 50, wind, rain, code,
                code >= 95 ? "Thunderstorm" : "Clear sky", true, "LIVE");
    }

    private static AlertRule rule(Metric metric, double threshold) {
        return new AlertRule("aman", "Dehradun", 30.32, 78.03, metric, threshold);
    }

    @Test
    void temperatureAboveTriggersWithSeverity() {
        assertThat(evaluator.evaluate(rule(Metric.TEMPERATURE_ABOVE, 32), weather(33.4, 5, 0, 0)))
                .get().satisfies(e -> {
                    assertThat(e.severity()).isEqualTo(Severity.WARNING);
                    assertThat(e.message()).contains("33.4").contains("32.0").contains("Dehradun");
                });
        assertThat(evaluator.evaluate(rule(Metric.TEMPERATURE_ABOVE, 32), weather(38, 5, 0, 0)))
                .get().extracting(AlertEvaluator.Evaluation::severity).isEqualTo(Severity.SEVERE);
        assertThat(evaluator.evaluate(rule(Metric.TEMPERATURE_ABOVE, 32), weather(31.9, 5, 0, 0))).isEmpty();
    }

    @Test
    void otherMetrics() {
        assertThat(evaluator.evaluate(rule(Metric.TEMPERATURE_BELOW, 5), weather(2, 5, 0, 0))).isPresent();
        assertThat(evaluator.evaluate(rule(Metric.WIND_ABOVE, 35), weather(20, 70, 0, 0)))
                .get().extracting(AlertEvaluator.Evaluation::severity).isEqualTo(Severity.SEVERE);
        assertThat(evaluator.evaluate(rule(Metric.PRECIPITATION_ABOVE, 1), weather(20, 5, 0.5, 61))).isEmpty();
    }

    @Test
    void thunderstormAndGaleAreSevereWeather() {
        assertThat(evaluator.severeWeather(weather(25, 10, 4, 95))).get()
                .extracting(AlertEvaluator.Evaluation::type).isEqualTo("SEVERE_WEATHER");
        assertThat(evaluator.severeWeather(weather(25, 65, 0, 3))).isPresent();
        assertThat(evaluator.severeWeather(weather(25, 10, 0, 3))).isEmpty();
    }

    @Test
    void cooldownBlocksRepeatAlerts() {
        AlertRule r = rule(Metric.WIND_ABOVE, 30);
        LocalDateTime now = LocalDateTime.now();
        assertThat(r.isCoolingDown(now, Duration.ofMinutes(60))).isFalse();
        r.markTriggered(now.minusMinutes(10));
        assertThat(r.isCoolingDown(now, Duration.ofMinutes(60))).isTrue();
        assertThat(r.isCoolingDown(now.plusMinutes(51), Duration.ofMinutes(60))).isFalse();
    }
}
