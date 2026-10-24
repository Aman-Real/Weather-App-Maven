package com.skycast.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** A triggered alert delivered to one user (stored for the history list, pushed live over SSE). */
@Entity
@Table(name = "weather_alerts")
@EntityListeners(AuditingEntityListener.class)
public class WeatherAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable: test and severe-weather alerts may not belong to a rule; LAZY because it is rarely needed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AlertRule rule;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "location_name", nullable = false, length = 120)
    private String locationName;

    private double latitude;
    private double longitude;

    @Column(nullable = false, length = 30)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "observed_value")
    private double observedValue;

    private double threshold;

    private boolean acknowledged;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected WeatherAlert() {
    }

    public WeatherAlert(AlertRule rule, String username, String locationName, double latitude, double longitude,
                        String type, Severity severity, String message, double observedValue, double threshold) {
        this.rule = rule;
        this.username = username;
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.observedValue = observedValue;
        this.threshold = threshold;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    public Long getId() { return id; }
    public AlertRule getRule() { return rule; }
    public String getUsername() { return username; }
    public String getLocationName() { return locationName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public double getObservedValue() { return observedValue; }
    public double getThreshold() { return threshold; }
    public boolean isAcknowledged() { return acknowledged; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
