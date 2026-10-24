package com.skycast.weather.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One stored reading of current conditions; the source for cached fallbacks and statistics. */
@Entity
@Table(name = "weather_snapshots", indexes = @Index(name = "idx_snapshot_location_time", columnList = "location_id, observed_at"))
public class WeatherSnapshot extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY many-to-one: use the fetch-join / entity-graph queries in WeatherSnapshotRepository when needed. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    private double temperature;

    @Column(name = "apparent_temperature")
    private double apparentTemperature;

    private int humidity;

    @Column(name = "wind_speed")
    private double windSpeed;

    private double precipitation;

    @Column(name = "weather_code")
    private int weatherCode;

    @Column(name = "is_day")
    private boolean day;

    protected WeatherSnapshot() {
    }

    public WeatherSnapshot(Location location, LocalDateTime observedAt, double temperature, double apparentTemperature,
                           int humidity, double windSpeed, double precipitation, int weatherCode, boolean day) {
        this.location = location;
        this.observedAt = observedAt;
        this.temperature = temperature;
        this.apparentTemperature = apparentTemperature;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
        this.precipitation = precipitation;
        this.weatherCode = weatherCode;
        this.day = day;
    }

    public Long getId() { return id; }
    public Location getLocation() { return location; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public double getTemperature() { return temperature; }
    public double getApparentTemperature() { return apparentTemperature; }
    public int getHumidity() { return humidity; }
    public double getWindSpeed() { return windSpeed; }
    public double getPrecipitation() { return precipitation; }
    public int getWeatherCode() { return weatherCode; }
    public boolean isDay() { return day; }
}
