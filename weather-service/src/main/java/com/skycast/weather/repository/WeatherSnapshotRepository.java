package com.skycast.weather.repository;

import com.skycast.weather.domain.WeatherSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WeatherSnapshotRepository extends JpaRepository<WeatherSnapshot, Long> {

    List<WeatherSnapshot> findTop24ByLocationIdOrderByObservedAtDesc(Long locationId);

    /** Native SQL query (LIMIT works on H2 and PostgreSQL). */
    @Query(value = "SELECT * FROM weather_snapshots WHERE location_id = :locationId ORDER BY observed_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<WeatherSnapshot> findLatestNative(@Param("locationId") Long locationId);

    /** JPQL fetch join: loads the LAZY location in the same SELECT (no N+1). */
    @Query("select s from WeatherSnapshot s join fetch s.location where s.observedAt >= :since order by s.observedAt desc")
    List<WeatherSnapshot> findSinceWithLocation(@Param("since") LocalDateTime since);

    /** Same effect declared with an entity graph. */
    @EntityGraph(attributePaths = "location")
    List<WeatherSnapshot> findByObservedAtAfter(LocalDateTime since);

    long countByLocationId(Long locationId);
}
