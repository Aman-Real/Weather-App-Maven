package com.skycast.weather.client;

import com.skycast.weather.TestData;
import com.skycast.weather.config.OpenMeteoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** RestClient + MockRestServiceServer: verifies URLs sent to Open-Meteo and JSON parsing, no network. */
class OpenMeteoClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenMeteoClient client = new OpenMeteoClient(builder, new OpenMeteoProperties(
            "https://api.open-meteo.com/v1/forecast",
            "https://archive-api.open-meteo.com/v1/archive",
            "https://geocoding-api.open-meteo.com/v1/search"));

    @Test
    void parsesCurrentConditions() {
        server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast?latitude=30.32&longitude=78.03&current=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TestData.raw("om-current.json"), MediaType.APPLICATION_JSON));

        OpenMeteoModels.Forecast result = client.current(30.32, 78.03);

        assertThat(result.current().temperature()).isEqualTo(24.6);
        assertThat(result.current().weatherCode()).isEqualTo(2);
        assertThat(result.current().isDay()).isEqualTo(1);
        assertThat(result.utcOffsetSeconds()).isEqualTo(19800);
        server.verify();
    }

    @Test
    void parsesForecastArrays() {
        server.expect(requestTo(containsString("forecast_days=7")))
                .andRespond(withSuccess(TestData.raw("om-forecast.json"), MediaType.APPLICATION_JSON));

        OpenMeteoModels.Forecast result = client.forecast(30.32, 78.03);

        assertThat(result.hourly().time()).hasSize(168);
        assertThat(result.daily().temperatureMax()).hasSize(7);
        server.verify();
    }

    @Test
    void archiveSendsDateRangeAndKeepsNulls() {
        server.expect(requestTo(startsWith("https://archive-api.open-meteo.com/v1/archive?latitude=30.32&longitude=78.03"
                        + "&start_date=2026-09-01&end_date=2026-09-04")))
                .andRespond(withSuccess(TestData.raw("om-archive.json"), MediaType.APPLICATION_JSON));

        OpenMeteoModels.Forecast result = client.archive(30.32, 78.03, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4));

        assertThat(result.daily().temperatureMax()).containsExactly(27.1, 28.4, 29.0, null);
        server.verify();
    }

    @Test
    void geocodingIgnoresUnknownFields() {
        server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/v1/search?name=Dehradun")))
                .andRespond(withSuccess(TestData.raw("om-geocoding.json"), MediaType.APPLICATION_JSON));

        OpenMeteoModels.Geocoding result = client.geocode("Dehradun");

        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).admin1()).isEqualTo("Uttarakhand");
        assertThat(result.results().get(1).admin1()).isNull();
        server.verify();
    }

    @Test
    void gridReturnsOneResultPerPoint() {
        server.expect(requestTo(containsString("latitude=29.0")))
                .andRespond(withSuccess(TestData.raw("om-grid.json"), MediaType.APPLICATION_JSON));
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            points.add(new double[]{29.0 + i * 0.5, 77.0 + i * 0.5});
        }

        List<OpenMeteoModels.Forecast> result = client.currentGrid(points);

        assertThat(result).hasSize(25);
        assertThat(result.get(3).current().temperature()).isEqualTo(23.0);
        server.verify();
    }
}
