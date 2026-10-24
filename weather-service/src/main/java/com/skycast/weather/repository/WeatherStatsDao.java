package com.skycast.weather.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Spring JDBC (no ORM): aggregate statistics straight from SQL with a RowMapper. */
@Repository
public class WeatherStatsDao {

    public record LocationStats(String location, long samples, double averageTemperature,
                                double maxTemperature, double minTemperature, LocalDateTime lastObserved) {
    }

    private static final String SQL = """
            SELECT l.name AS location, COUNT(s.id) AS samples,
                   AVG(s.temperature) AS avg_temp, MAX(s.temperature) AS max_temp,
                   MIN(s.temperature) AS min_temp, MAX(s.observed_at) AS last_observed
            FROM locations l
            JOIN weather_snapshots s ON s.location_id = l.id
            WHERE s.observed_at >= :since
            GROUP BY l.name
            ORDER BY samples DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public WeatherStatsDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LocationStats> statsSince(LocalDateTime since) {
        return jdbc.query(SQL, new MapSqlParameterSource("since", Timestamp.valueOf(since)), (rs, i) -> new LocationStats(
                rs.getString("location"),
                rs.getLong("samples"),
                round(rs.getDouble("avg_temp")),
                rs.getDouble("max_temp"),
                rs.getDouble("min_temp"),
                rs.getTimestamp("last_observed").toLocalDateTime()));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
