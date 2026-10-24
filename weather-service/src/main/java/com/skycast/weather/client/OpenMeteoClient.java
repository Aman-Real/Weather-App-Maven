package com.skycast.weather.client;

import com.skycast.weather.client.OpenMeteoModels.Forecast;
import com.skycast.weather.client.OpenMeteoModels.Geocoding;
import com.skycast.weather.config.OpenMeteoProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Thin RestClient wrapper around the Open-Meteo forecast, archive and geocoding APIs. */
@Component
public class OpenMeteoClient {

    static final String CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,"
            + "weather_code,wind_speed_10m,wind_direction_10m,is_day";
    static final String HOURLY = "temperature_2m,precipitation_probability,weather_code";
    static final String DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max";
    static final String GRID = "temperature_2m,wind_speed_10m,wind_direction_10m,weather_code";

    private final RestClient rest;
    private final OpenMeteoProperties props;

    public OpenMeteoClient(RestClient.Builder builder, OpenMeteoProperties props) {
        this.rest = builder.build();
        this.props = props;
    }

    public Forecast current(double lat, double lon) {
        return rest.get()
                .uri(props.forecastUrl() + "?latitude={lat}&longitude={lon}&current={current}&timezone=auto",
                        lat, lon, CURRENT)
                .retrieve()
                .body(Forecast.class);
    }

    public Forecast forecast(double lat, double lon) {
        return rest.get()
                .uri(props.forecastUrl() + "?latitude={lat}&longitude={lon}&hourly={hourly}&daily={daily}"
                        + "&timezone=auto&forecast_days=7", lat, lon, HOURLY, DAILY)
                .retrieve()
                .body(Forecast.class);
    }

    public Forecast archive(double lat, double lon, LocalDate start, LocalDate end) {
        return rest.get()
                .uri(props.archiveUrl() + "?latitude={lat}&longitude={lon}&start_date={start}&end_date={end}"
                        + "&daily={daily}&timezone=auto", lat, lon, start, end, DAILY)
                .retrieve()
                .body(Forecast.class);
    }

    public Geocoding geocode(String name) {
        return rest.get()
                .uri(props.geocodingUrl() + "?name={name}&count=8&language=en&format=json", name)
                .retrieve()
                .body(Geocoding.class);
    }

    /** Several coordinates in one request; Open-Meteo then answers with a JSON list (needs 2+ points). */
    public List<Forecast> currentGrid(List<double[]> points) {
        String lats = points.stream().map(p -> String.valueOf(p[0])).collect(Collectors.joining(","));
        String lons = points.stream().map(p -> String.valueOf(p[1])).collect(Collectors.joining(","));
        return rest.get()
                .uri(props.forecastUrl() + "?latitude={lats}&longitude={lons}&current={current}&timezone=auto",
                        lats, lons, GRID)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Forecast>>() {
                });
    }
}
