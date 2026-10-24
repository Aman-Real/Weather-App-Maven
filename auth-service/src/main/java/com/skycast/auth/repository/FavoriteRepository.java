package com.skycast.auth.repository;

import com.skycast.auth.domain.FavoriteLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<FavoriteLocation, Long> {

    Optional<FavoriteLocation> findByIdAndUserUsername(Long id, String username);

    boolean existsByUserUsernameAndLatitudeAndLongitude(String username, double latitude, double longitude);
}
