package com.skycast.weather.domain;

import java.time.LocalDateTime;

/**
 * Plain class with NO JPA annotations: it is mapped to the fetch_audit_log table purely in
 * XML (src/main/resources/META-INF/orm.xml) to show XML-based entity mapping.
 */
public class FetchAuditLog {

    private Long id;
    private String endpoint;
    private double latitude;
    private double longitude;
    private boolean success;
    private long durationMs;
    private String message;
    private LocalDateTime createdAt;

    protected FetchAuditLog() {
    }

    public FetchAuditLog(String endpoint, double latitude, double longitude, boolean success, long durationMs, String message) {
        this.endpoint = endpoint;
        this.latitude = latitude;
        this.longitude = longitude;
        this.success = success;
        this.durationMs = durationMs;
        this.message = message == null ? null : message.substring(0, Math.min(message.length(), 250));
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
