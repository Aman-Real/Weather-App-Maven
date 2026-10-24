package com.skycast.alert.service;

import com.skycast.alert.domain.AlertRule;
import com.skycast.alert.domain.Metric;
import com.skycast.alert.domain.Severity;
import com.skycast.alert.domain.WeatherAlert;
import com.skycast.alert.repository.AlertRuleRepository;
import com.skycast.alert.repository.WeatherAlertRepository;
import com.skycast.common.dto.AlertDto;
import com.skycast.common.dto.AlertRuleDto;
import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AlertService {

    public static final int MAX_RULES_PER_USER = 25;

    private final AlertRuleRepository rules;
    private final WeatherAlertRepository alerts;
    private final AlertEvaluator evaluator;
    private final AlertStreamService stream;
    private final Duration cooldown;

    public AlertService(AlertRuleRepository rules, WeatherAlertRepository alerts, AlertEvaluator evaluator,
                        AlertStreamService stream, @Value("${skycast.alerts.cooldown:PT60M}") Duration cooldown) {
        this.rules = rules;
        this.alerts = alerts;
        this.evaluator = evaluator;
        this.stream = stream;
        this.cooldown = cooldown;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleDto> rules(String username) {
        return rules.findByUsernameOrderByCreatedAtDesc(username).stream().map(AlertService::toDto).toList();
    }

    @Transactional
    public AlertRuleDto createRule(String username, AlertRuleDto dto) {
        if (rules.countByUsername(username) >= MAX_RULES_PER_USER) {
            throw ApiException.badRequest("At most " + MAX_RULES_PER_USER + " alert rules per user");
        }
        AlertRule rule = new AlertRule(username, dto.locationName().trim(), round(dto.latitude()), round(dto.longitude()),
                Metric.valueOf(dto.metric()), dto.threshold());
        rule.setActive(dto.active());
        return toDto(rules.save(rule));
    }

    @Transactional
    public AlertRuleDto setActive(String username, Long id, boolean active) {
        AlertRule rule = rules.findByIdAndUsername(id, username).orElseThrow(() -> ApiException.notFound("Rule not found"));
        rule.setActive(active);
        return toDto(rule);
    }

    @Transactional
    public void deleteRule(String username, Long id) {
        AlertRule rule = rules.findByIdAndUsername(id, username).orElseThrow(() -> ApiException.notFound("Rule not found"));
        alerts.detachFromRule(rule.getId());
        rules.delete(rule);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> recent(String username) {
        return alerts.findTop50ByUsernameOrderByCreatedAtDesc(username).stream().map(AlertService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> recentForAll() {
        return alerts.findTop100ByOrderByCreatedAtDesc().stream().map(AlertService::toDto).toList();
    }

    @Transactional
    public AlertDto acknowledge(String username, Long id) {
        WeatherAlert alert = alerts.findByIdAndUsername(id, username).orElseThrow(() -> ApiException.notFound("Alert not found"));
        alert.acknowledge();
        return toDto(alert);
    }

    @Transactional
    public int acknowledgeAll(String username) {
        return alerts.acknowledgeAll(username);
    }

    @Transactional(readOnly = true)
    public List<AlertRule> activeRules() {
        return rules.findByActiveTrue();
    }

    /**
     * Evaluates the given rules (all for the same location) against one reading, stores the alerts
     * and pushes them to connected browsers. Returns the number of alerts raised.
     */
    @Transactional
    public int process(List<Long> ruleIds, CurrentWeatherDto weather) {
        LocalDateTime now = LocalDateTime.now();
        Optional<AlertEvaluator.Evaluation> severe = evaluator.severeWeather(weather);
        Set<String> severeNotified = new HashSet<>();
        List<WeatherAlert> raised = new ArrayList<>();

        for (AlertRule rule : rules.findAllById(ruleIds)) {
            if (!rule.isActive() || rule.isCoolingDown(now, cooldown)) {
                continue;
            }
            Optional<AlertEvaluator.Evaluation> hit = evaluator.evaluate(rule, weather);
            if (hit.isEmpty() && severe.isPresent() && severeNotified.add(rule.getUsername())) {
                hit = severe;
            }
            hit.ifPresent(e -> {
                rule.markTriggered(now);
                raised.add(new WeatherAlert(rule, rule.getUsername(), rule.getLocationName(), rule.getLatitude(),
                        rule.getLongitude(), e.type(), e.severity(), e.message(), e.observed(), e.threshold()));
            });
        }
        alerts.saveAll(raised);
        alerts.flush();
        raised.forEach(a -> stream.publish(a.getUsername(), toDto(a)));
        return raised.size();
    }

    /** Demo helper for the viva: raises an INFO alert for the caller and pushes it immediately. */
    @Transactional
    public AlertDto testAlert(String username) {
        WeatherAlert alert = alerts.saveAndFlush(new WeatherAlert(null, username, "SkyCast", 0, 0, "TEST", Severity.INFO,
                "This is a test alert - live notifications are working", 0, 0));
        AlertDto dto = toDto(alert);
        stream.publish(username, dto);
        return dto;
    }

    static AlertDto toDto(WeatherAlert a) {
        return new AlertDto(a.getId(), a.getLocationName(), a.getLatitude(), a.getLongitude(), a.getType(),
                a.getSeverity().name(), a.getMessage(), a.getObservedValue(), a.getThreshold(), a.isAcknowledged(),
                a.getCreatedAt());
    }

    static AlertRuleDto toDto(AlertRule r) {
        return new AlertRuleDto(r.getId(), r.getLocationName(), r.getLatitude(), r.getLongitude(), r.getMetric().name(),
                r.getThreshold(), r.isActive());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
