package com.skycast.weather.repository;

import com.skycast.weather.domain.Location;

import java.util.List;

/** Custom repository fragment implemented with the Criteria API and native Hibernate HQL. */
public interface LocationRepositoryCustom {

    List<Location> search(String name, String country, Double minLatitude, Double maxLatitude);

    List<Location> recentlyUpdated(int limit);
}
