package com.skycast.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDateTime;

/** A user's subscription: "tell me when <metric> crosses <threshold> at <location>". */
@Entity
@Table(name = "alert_rules")
@EntityListeners(AuditingEntityListener.class)
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "location_name", nullable = false, length = 120)
    private String locationName;

    private double latitude;
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Metric metric;

    private double threshold;

    private boolean active = true;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected AlertRule() {
    }

    public AlertRule(String username, String locationName, double latitude, double longitude, Metric metric, double threshold) {
        this.username = username;
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.metric = metric;
        this.threshold = threshold;
    }

    public boolean isCoolingDown(LocalDateTime now, Duration cooldown) {
        return lastTriggeredAt != null && lastTriggeredAt.plus(cooldown).isAfter(now);
    }

    public void markTriggered(LocalDateTime when) {
        this.lastTriggeredAt = when;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getLocationName() { return locationName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Metric getMetric() { return metric; }
    public double getThreshold() { return threshold; }
    public boolean isActive() { return active; }
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
