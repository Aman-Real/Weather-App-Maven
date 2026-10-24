package com.skycast.weather.service;

import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.dto.ForecastDto;
import com.skycast.common.error.ApiException;
import com.skycast.weather.TestData;
import com.skycast.weather.client.OpenMeteoClient;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.Location;
import com.skycast.weather.domain.WeatherSnapshot;
import com.skycast.weather.support.WeatherMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit test (Mockito): live path, circuit-breaker fallback to the stored snapshot, and 503. */
class WeatherServiceTest {

    private final OpenMeteoClient client = mock(OpenMeteoClient.class);
    private final LocationService locations = mock(LocationService.class);
    private final SnapshotService snapshots = mock(SnapshotService.class);
    private final FetchAuditService audit = mock(FetchAuditService.class);
    private WeatherService service;
    private Location dehradun;

    /** Pass-through breaker: runs the call and routes any exception to the fallback, like an open/failed breaker. */
    private static final CircuitBreaker PASS_THROUGH = new CircuitBreaker() {
        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            } catch (RuntimeException e) {
                return fallback.apply(e);
            }
        }
    };

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
        when(factory.create(anyString())).thenReturn(PASS_THROUGH);
        service = new WeatherService(client, factory, locations, snapshots, audit,
                new WeatherMapper(TestData.translator()), new SimpleMeterRegistry());

        dehradun = new Location("Dehradun", "India", 30.32, 78.03);
        ReflectionTestUtils.setField(dehradun, "id", 1L);
        when(locations.resolve(any(), any(), anyDouble(), anyDouble())).thenReturn(dehradun);
    }

    @Test
    void liveReadingIsReturnedAndStored() {
        OpenMeteoModels.Forecast live = TestData.forecast("om-current.json");
        when(client.current(30.32, 78.03)).thenReturn(live);

        CurrentWeatherDto dto = service.current(30.32, 78.03, "Dehradun");

        assertThat(dto.source()).isEqualTo("LIVE");
        assertThat(dto.temperature()).isEqualTo(24.6);
        assertThat(dto.condition()).isEqualTo("Partly cloudy");
        assertThat(dto.humidity()).isEqualTo(58);
        verify(snapshots).record(1L, live.current());
        verify(audit).log(eq("current"), anyDouble(), anyDouble(), eq(true), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void providerFailureFallsBackToLastSnapshot() {
        when(client.current(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("timeout"));
        WeatherSnapshot stored = new WeatherSnapshot(dehradun, LocalDateTime.of(2026, 10, 24, 12, 0),
                21.0, 20.5, 60, 7.0, 0.0, 3, true);
        when(snapshots.latest(1L)).thenReturn(Optional.of(stored));

        CurrentWeatherDto dto = service.current(30.32, 78.03, "Dehradun");

        assertThat(dto.source()).isEqualTo("CACHED");
        assertThat(dto.temperature()).isEqualTo(21.0);
        assertThat(dto.condition()).isEqualTo("Overcast");
        verify(snapshots, never()).record(any(), any());
    }

    @Test
    void noLiveDataAndNoSnapshotIs503() {
        when(client.current(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("down"));
        when(snapshots.latest(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current(30.32, 78.03, "Dehradun"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void forecastKeepsFutureHoursAndSevenDays() {
        when(client.forecast(30.32, 78.03)).thenReturn(TestData.forecast("om-forecast.json"));

        ForecastDto dto = service.forecast(30.3165, 78.0322);

        assertThat(dto.daily()).hasSize(7);
        assertThat(dto.hourly()).hasSize(48);
        assertThat(dto.daily().get(4).condition()).isEqualTo("Thunderstorm");
    }

    @Test
    void forecastFailureIs503() {
        when(client.forecast(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("down"));

        assertThatThrownBy(() -> service.forecast(30.32, 78.03))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unavailable");
    }
}
