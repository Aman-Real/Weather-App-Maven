package com.skycast.weather.repository;

import com.skycast.weather.config.JpaConfig;
import com.skycast.weather.domain.DailyWeather;
import com.skycast.weather.domain.FetchAuditLog;
import com.skycast.weather.domain.Location;
import com.skycast.weather.domain.WeatherSnapshot;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test of the ORM layer on H2 with the real Flyway migrations (V1 schema + V2 seed of 7 locations).
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest(properties = "eureka.client.enabled=false")
@Import({JpaConfig.class, WeatherStatsDao.class})
class WeatherRepositoryTest {

    @Autowired private LocationRepository locations;
    @Autowired private WeatherSnapshotRepository snapshots;
    @Autowired private DailyWeatherRepository daily;
    @Autowired private FetchAuditLogRepository audit;
    @Autowired private WeatherStatsDao stats;
    @Autowired private EntityManager em;

    @Test
    void flywaySeedIsLoaded() {
        assertThat(locations.count()).isEqualTo(7);
        assertThat(locations.findByLatitudeAndLongitude(30.32, 78.03)).get()
                .extracting(Location::getName).isEqualTo("Dehradun");
    }

    @Test
    void derivedJpqlAndCriteriaQueries() {
        assertThat(locations.findTop10ByNameContainingIgnoreCaseOrderByNameAsc("de"))
                .extracting(Location::getName).contains("Dehradun", "New Delhi");
        assertThat(locations.findByCountry("india")).hasSize(3);
        assertThat(locations.search(null, "India", 20.0, null))
                .extracting(Location::getName).containsExactly("Dehradun", "New Delhi");
        assertThat(locations.search("sing", null, null, null)).hasSize(1);
    }

    @Test
    void hqlThroughHibernateSession() {
        assertThat(locations.recentlyUpdated(3)).hasSize(3);
    }

    @Test
    void auditingAndOptimisticVersioning() {
        Location saved = locations.saveAndFlush(new Location("Dehra Test", "India", 30.11, 78.11));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        saved.setName("Renamed");
        locations.saveAndFlush(saved);

        assertThat(saved.getVersion()).isEqualTo(1L);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void pessimisticLockQueryRuns() {
        Long id = locations.findByLatitudeAndLongitude(1.29, 103.85).orElseThrow().getId();
        assertThat(locations.findByIdForUpdate(id)).isPresent();
    }

    @Test
    void nativeQueryAndFetchJoin() {
        Location dehradun = locations.findByLatitudeAndLongitude(30.32, 78.03).orElseThrow();
        snapshots.save(new WeatherSnapshot(dehradun, LocalDateTime.now(), 26.0, 27.0, 50, 5.0, 0.0, 0, true));
        snapshots.flush();
        em.clear();

        WeatherSnapshot latest = snapshots.findLatestNative(dehradun.getId()).orElseThrow();
        assertThat(latest.getTemperature()).isEqualTo(26.0);

        em.clear();
        List<WeatherSnapshot> joined = snapshots.findSinceWithLocation(LocalDateTime.now().minusHours(2));
        assertThat(joined).isNotEmpty();
        assertThat(Hibernate.isInitialized(joined.get(0).getLocation())).isTrue();

        em.clear();
        WeatherSnapshot lazy = snapshots.findById(latest.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(lazy.getLocation())).isFalse();
    }

    @Test
    void namedQueryFromOrmXml() {
        Location mumbai = locations.findByLatitudeAndLongitude(19.08, 72.88).orElseThrow();
        for (int d = 1; d <= 5; d++) {
            DailyWeather row = new DailyWeather(mumbai, LocalDate.of(2026, 8, d));
            row.update(31 + d, 25, d * 2.0, 15, 61);
            daily.save(row);
        }
        daily.flush();

        List<DailyWeather> range = daily.findRange(mumbai.getId(), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 4));
        assertThat(range).extracting(DailyWeather::getDate)
                .containsExactly(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4));
    }

    @Test
    void xmlMappedEntityAndNamedNativeQuery() {
        audit.save(new FetchAuditLog("forecast", 30.32, 78.03, false, 120, "timeout"));
        audit.save(new FetchAuditLog("forecast", 30.32, 78.03, true, 80, null));
        audit.flush();

        assertThat(audit.countBySuccessFalse()).isEqualTo(1);
        List<Object[]> rows = audit.failuresByEndpoint();
        assertThat(rows).hasSize(1);
        assertThat(String.valueOf(rows.get(0)[0])).isEqualTo("forecast");
    }

    @Test
    void jdbcTemplateStatistics() {
        List<WeatherStatsDao.LocationStats> result = stats.statsSince(LocalDateTime.now().minusDays(1));
        assertThat(result).hasSize(7);
        assertThat(result.get(0).samples()).isGreaterThanOrEqualTo(1);
    }
}
