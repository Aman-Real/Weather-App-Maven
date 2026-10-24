package com.skycast.alert.repository;

import com.skycast.alert.domain.WeatherAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeatherAlertRepository extends JpaRepository<WeatherAlert, Long> {

    List<WeatherAlert> findTop50ByUsernameOrderByCreatedAtDesc(String username);

    List<WeatherAlert> findTop100ByOrderByCreatedAtDesc();

    Optional<WeatherAlert> findByIdAndUsername(Long id, String username);

    long countByUsernameAndAcknowledgedFalse(String username);

    /** Bulk JPQL update (bypasses the persistence context, hence clearAutomatically). */
    @Modifying(clearAutomatically = true)
    @Query("update WeatherAlert a set a.acknowledged = true where a.username = :username and a.acknowledged = false")
    int acknowledgeAll(@Param("username") String username);

    @Modifying
    @Query("update WeatherAlert a set a.rule = null where a.rule.id = :ruleId")
    int detachFromRule(@Param("ruleId") Long ruleId);
}
