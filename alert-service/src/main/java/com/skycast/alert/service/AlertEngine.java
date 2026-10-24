package com.skycast.alert.service;

import com.skycast.alert.client.WeatherClient;
import com.skycast.alert.domain.AlertRule;
import com.skycast.common.dto.CurrentWeatherDto;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real-time alerting loop: every few minutes, fetch current weather ONCE per subscribed location
 * (Feign -> weather-service) and evaluate all rules for that location. HTTP calls happen outside
 * any database transaction; AlertService.process(..) is the transactional part.
 */
@Component
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertService alerts;
    private final WeatherClient weather;
    private final MeterRegistry meters;

    public AlertEngine(AlertService alerts, WeatherClient weather, MeterRegistry meters) {
        this.alerts = alerts;
        this.weather = weather;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${skycast.alerts.interval:PT5M}", initialDelayString = "${skycast.alerts.initial-delay:PT30S}")
    public void scheduledRun() {
        evaluateNow();
    }

    public int evaluateNow() {
        Map<String, List<AlertRule>> byLocation = alerts.activeRules().stream()
                .collect(Collectors.groupingBy(r -> String.format(Locale.ROOT, "%.2f:%.2f", r.getLatitude(), r.getLongitude())));
        int raised = 0;
        for (List<AlertRule> group : byLocation.values()) {
            AlertRule first = group.get(0);
            CurrentWeatherDto current = weather.current(first.getLatitude(), first.getLongitude(), first.getLocationName());
            if (current == null) {
                continue; // fallback: weather-service unavailable
            }
            raised += alerts.process(group.stream().map(AlertRule::getId).toList(), current);
        }
        meters.counter("skycast.alerts.raised").increment(raised);
        log.info("Alert evaluation: {} locations, {} alerts raised", byLocation.size(), raised);
        return raised;
    }
}
