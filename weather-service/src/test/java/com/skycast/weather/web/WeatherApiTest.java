package com.skycast.weather.web;

import com.skycast.weather.TestData;
import com.skycast.weather.client.OpenMeteoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole application context (security, JPA, Flyway, cache, AOP, Batch, circuit breaker) with only the
 * outbound Open-Meteo client mocked.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "skycast.scheduling.enabled=false"})
@AutoConfigureMockMvc
class WeatherApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OpenMeteoClient client;

    @Test
    void currentWeatherIsLive() throws Exception {
        when(client.current(anyDouble(), anyDouble())).thenReturn(TestData.forecast("om-current.json"));

        mvc.perform(get("/api/weather/current").param("lat", "28.61").param("lon", "77.21").param("name", "New Delhi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LIVE"))
                .andExpect(jsonPath("$.temperature").value(24.6))
                .andExpect(jsonPath("$.locationName").value("New Delhi"));
    }

    @Test
    void providerDownServesSeededSnapshot() throws Exception {
        when(client.current(eq(19.08), eq(72.88))).thenThrow(new ResourceAccessException("down"));

        mvc.perform(get("/api/weather/current").param("lat", "19.08").param("lon", "72.88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CACHED"))
                .andExpect(jsonPath("$.locationName").value("Mumbai"));
    }

    @Test
    void forecastEndpoint() throws Exception {
        when(client.forecast(anyDouble(), anyDouble())).thenReturn(TestData.forecast("om-forecast.json"));

        mvc.perform(get("/api/weather/forecast").param("lat", "25.2").param("lon", "55.27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.hourly.length()").value(48));
    }

    @Test
    void historyIsFetchedThenServedFromDatabase() throws Exception {
        when(client.archive(anyDouble(), anyDouble(), any(), any())).thenReturn(TestData.forecast("om-archive.json"));

        mvc.perform(get("/api/weather/history").param("lat", "24.71").param("lon", "46.68")
                        .param("start", "2026-09-01").param("end", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ARCHIVE"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.stats.hottestDay").value("2026-09-03"));

        mvc.perform(get("/api/weather/history").param("lat", "24.71").param("lon", "46.68")
                        .param("start", "2026-09-01").param("end", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DATABASE"));
    }

    @Test
    void invalidInputIsBadRequest() throws Exception {
        mvc.perform(get("/api/weather/current").param("lat", "200").param("lon", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/weather/history").param("lat", "10").param("lon", "10")
                        .param("start", LocalDate.now().toString()).param("end", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statisticsNeedAdminRole() throws Exception {
        mvc.perform(get("/api/weather/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/weather/stats").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/weather/stats").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void adminRunsSpringBatchHistoryImport() throws Exception {
        when(client.archive(anyDouble(), anyDouble(), any(), any())).thenReturn(TestData.forecast("om-archive.json"));

        mvc.perform(post("/api/weather/admin/history-import").param("days", "4")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.read").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
    }

    @Test
    void thymeleafStatusPageRendersAView() throws Exception {
        mvc.perform(get("/status").param("country", "India"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("status"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Dehradun")));
    }

    @Test
    void locationSearchAndHealth() throws Exception {
        mvc.perform(get("/api/weather/locations").param("country", "India"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
