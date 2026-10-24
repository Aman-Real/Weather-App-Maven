package com.skycast.weather.domain;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A place we have weather for. Coordinates are rounded to 2 decimals (~1 km) and unique.
 * Cached in the Hibernate second-level cache (read often, changed rarely).
 */
@Entity
@Table(name = "locations", uniqueConstraints = @UniqueConstraint(columnNames = {"latitude", "longitude"}))
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "locations")
public class Location extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    /** Optimistic locking: concurrent updates of the same row fail with OptimisticLockException. */
    @Version
    private Long version;

    /** LAZY one-to-many: snapshots are only loaded when explicitly accessed or fetch-joined. */
    @OneToMany(mappedBy = "location")
    @OrderBy("observedAt DESC")
    private List<WeatherSnapshot> snapshots = new ArrayList<>();

    protected Location() {
    }

    public Location(String name, String country, double latitude, double longitude) {
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Long getVersion() { return version; }
    public List<WeatherSnapshot> getSnapshots() { return snapshots; }
}
