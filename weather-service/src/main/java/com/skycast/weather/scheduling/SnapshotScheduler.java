package com.skycast.weather.scheduling;

import com.skycast.weather.domain.Location;
import com.skycast.weather.repository.LocationRepository;
import com.skycast.weather.service.WeatherService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes current conditions for known locations, building up the snapshot history
 * used by statistics and by the circuit-breaker fallback. Assumes ONE replica (see helm values).
 */
@Component
@ConditionalOnProperty(prefix = "skycast.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);
    private static final int MAX_LOCATIONS = 50;

    private final LocationRepository locations;
    private final WeatherService weather;

    public SnapshotScheduler(LocationRepository locations, WeatherService weather) {
        this.locations = locations;
        this.weather = weather;
    }

    /** Bean lifecycle callback after dependency injection. */
    @PostConstruct
    void started() {
        log.info("Snapshot scheduler active (max {} locations per run)", MAX_LOCATIONS);
    }

    /** Bean lifecycle callback before the context shuts down. */
    @PreDestroy
    void stopping() {
        log.info("Snapshot scheduler stopping");
    }

    @Scheduled(fixedDelayString = "${skycast.snapshot.interval:PT15M}",
            initialDelayString = "${skycast.snapshot.initial-delay:PT1M}")
    public void refreshSnapshots() {
        int ok = 0;
        for (Location l : locations.findAll(PageRequest.of(0, MAX_LOCATIONS, Sort.by("updatedAt").descending()))) {
            try {
                weather.current(l.getLatitude(), l.getLongitude(), l.getName());
                ok++;
            } catch (RuntimeException e) {
                log.warn("Snapshot refresh failed for {}: {}", l.getName(), e.getMessage());
            }
        }
        log.info("Snapshot refresh finished: {} locations", ok);
    }
}
