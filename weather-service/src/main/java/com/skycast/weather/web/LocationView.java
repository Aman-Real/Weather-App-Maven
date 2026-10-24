package com.skycast.weather.web;

import com.skycast.weather.domain.Location;

import java.time.LocalDateTime;

public record LocationView(Long id, String name, String country, double latitude, double longitude,
                           Long version, LocalDateTime updatedAt) {

    public static LocationView of(Location l) {
        return new LocationView(l.getId(), l.getName(), l.getCountry(), l.getLatitude(), l.getLongitude(),
                l.getVersion(), l.getUpdatedAt());
    }
}
