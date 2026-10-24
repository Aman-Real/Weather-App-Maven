package com.skycast.weather.repository;

import com.skycast.weather.domain.DailyWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyWeatherRepository extends JpaRepository<DailyWeather, Long> {

    /** Backed by the named query "DailyWeather.findRange" declared in META-INF/orm.xml. */
    List<DailyWeather> findRange(@Param("locationId") Long locationId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    long countByLocationId(Long locationId);
}
