package com.skycast.weather.repository;

import com.skycast.weather.domain.Location;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long>, LocationRepositoryCustom {

    /** Derived query method. */
    Optional<Location> findByLatitudeAndLongitude(double latitude, double longitude);

    /** Derived query with keyword + ordering. */
    List<Location> findTop10ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    /** JPQL with a named parameter. */
    @Query("select l from Location l where lower(l.country) = lower(:country) order by l.name")
    List<Location> findByCountry(@Param("country") String country);

    /** Pessimistic locking: SELECT ... FOR UPDATE, other writers wait until this transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Location l where l.id = :id")
    Optional<Location> findByIdForUpdate(@Param("id") Long id);
}
