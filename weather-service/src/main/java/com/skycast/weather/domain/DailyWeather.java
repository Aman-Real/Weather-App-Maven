package com.skycast.weather.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** Historical daily summary (from the Open-Meteo archive). Named query DailyWeather.findRange lives in META-INF/orm.xml. */
@Entity
@Table(name = "daily_weather", uniqueConstraints = @UniqueConstraint(columnNames = {"location_id", "weather_date"}))
public class DailyWeather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "weather_date", nullable = false)
    private LocalDate date;

    @Column(name = "temp_max")
    private double tempMax;

    @Column(name = "temp_min")
    private double tempMin;

    @Column(name = "precipitation_sum")
    private double precipitationSum;

    @Column(name = "wind_speed_max")
    private double windSpeedMax;

    @Column(name = "weather_code")
    private int weatherCode;

    protected DailyWeather() {
    }

    public DailyWeather(Location location, LocalDate date) {
        this.location = location;
        this.date = date;
    }

    public void update(double tempMax, double tempMin, double precipitationSum, double windSpeedMax, int weatherCode) {
        this.tempMax = tempMax;
        this.tempMin = tempMin;
        this.precipitationSum = precipitationSum;
        this.windSpeedMax = windSpeedMax;
        this.weatherCode = weatherCode;
    }

    public Long getId() { return id; }
    public Location getLocation() { return location; }
    public LocalDate getDate() { return date; }
    public double getTempMax() { return tempMax; }
    public double getTempMin() { return tempMin; }
    public double getPrecipitationSum() { return precipitationSum; }
    public double getWindSpeedMax() { return windSpeedMax; }
    public int getWeatherCode() { return weatherCode; }
}
