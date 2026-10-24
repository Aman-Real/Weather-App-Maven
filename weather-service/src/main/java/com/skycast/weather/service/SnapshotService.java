package com.skycast.weather.service;

import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.Location;
import com.skycast.weather.domain.WeatherSnapshot;
import com.skycast.weather.repository.LocationRepository;
import com.skycast.weather.repository.WeatherSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SnapshotService {

    private final WeatherSnapshotRepository snapshots;
    private final LocationRepository locations;

    public SnapshotService(WeatherSnapshotRepository snapshots, LocationRepository locations) {
        this.snapshots = snapshots;
        this.locations = locations;
    }

    /** Stores a reading unless the provider's observation time has not changed since the last one. */
    @Transactional
    public void record(Long locationId, OpenMeteoModels.Current c) {
        LocalDateTime observedAt = c.time() == null ? LocalDateTime.now() : LocalDateTime.parse(c.time());
        Optional<WeatherSnapshot> latest = snapshots.findLatestNative(locationId);
        if (latest.isPresent() && latest.get().getObservedAt().equals(observedAt)) {
            return;
        }
        Location ref = locations.getReferenceById(locationId);
        snapshots.save(new WeatherSnapshot(ref, observedAt,
                val(c.temperature()), val(c.apparentTemperature()), (int) Math.round(val(c.humidity())),
                val(c.windSpeed()), val(c.precipitation()),
                c.weatherCode() == null ? 0 : c.weatherCode(), c.isDay() == null || c.isDay() == 1));
    }

    @Transactional(readOnly = true)
    public Optional<WeatherSnapshot> latest(Long locationId) {
        return snapshots.findLatestNative(locationId);
    }

    @Transactional(readOnly = true)
    public List<WeatherSnapshot> recent(Long locationId) {
        return snapshots.findTop24ByLocationIdOrderByObservedAtDesc(locationId);
    }

    private static double val(Double v) {
        return v == null ? 0.0 : v;
    }
}
