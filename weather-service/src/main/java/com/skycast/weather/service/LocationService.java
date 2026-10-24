package com.skycast.weather.service;

import com.skycast.common.error.ApiException;
import com.skycast.weather.domain.Location;
import com.skycast.weather.repository.LocationRepository;
import com.skycast.weather.support.Geo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    /** Get-or-create by rounded coordinates (REQUIRED propagation: joins the caller's transaction if any). */
    @Transactional
    public Location resolve(String name, String country, double lat, double lon) {
        double rlat = Geo.round2(lat);
        double rlon = Geo.round2(lon);
        boolean named = name != null && !name.isBlank();
        return locations.findByLatitudeAndLongitude(rlat, rlon)
                .map(existing -> {
                    if (named && existing.getName().startsWith("Lat ")) {
                        existing.setName(name.trim()); // dirty checking -> UPDATE + @Version increment on commit
                    }
                    return existing;
                })
                .orElseGet(() -> locations.save(new Location(named ? name.trim() : Geo.label(rlat, rlon), country, rlat, rlon)));
    }

    /** Pessimistic lock while renaming, so two admins cannot overwrite each other silently. */
    @Transactional
    public Location rename(Long id, String newName) {
        Location location = locations.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Location " + id + " not found"));
        location.setName(newName.trim());
        return location;
    }

    @Transactional(readOnly = true)
    public List<Location> search(String name, String country, Double minLat, Double maxLat) {
        return locations.search(name, country, minLat, maxLat);
    }

    @Transactional(readOnly = true)
    public List<Location> recentlyUpdated(int limit) {
        return locations.recentlyUpdated(Math.max(1, Math.min(limit, 50)));
    }
}
