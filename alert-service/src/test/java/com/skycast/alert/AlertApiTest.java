package com.skycast.alert;

import com.skycast.alert.client.WeatherClient;
import com.skycast.common.dto.CurrentWeatherDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full context; weather-service (Feign) is mocked and requests carry a mock JWT. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "skycast.scheduling.enabled=false"})
@AutoConfigureMockMvc
class AlertApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeatherClient weatherClient;

    @Test
    void rulesNeedAuthentication() throws Exception {
        mvc.perform(get("/api/alerts/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRuleEvaluateAndListAlerts() throws Exception {
        mvc.perform(post("/api/alerts/rules").with(jwt().jwt(j -> j.subject("riya")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationName\":\"Dubai\",\"latitude\":25.2,\"longitude\":55.27,"
                                + "\"metric\":\"TEMPERATURE_ABOVE\",\"threshold\":35,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        when(weatherClient.current(anyDouble(), anyDouble(), anyString())).thenReturn(new CurrentWeatherDto(
                "Dubai", 25.2, 55.27, LocalDateTime.now(), 39.5, 41.0, 30, 12.0, 0.0, 0, "Clear sky", true, "LIVE"));

        mvc.perform(post("/api/alerts/admin/evaluate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raised").value(1));

        mvc.perform(get("/api/alerts").with(jwt().jwt(j -> j.subject("riya"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TEMPERATURE_ABOVE"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"));

        // cooldown: a second run does not repeat the alert
        mvc.perform(post("/api/alerts/admin/evaluate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(jsonPath("$.raised").value(0));
    }

    @Test
    void invalidMetricIsRejected() throws Exception {
        mvc.perform(post("/api/alerts/rules").with(jwt().jwt(j -> j.subject("riya")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationName\":\"Dubai\",\"latitude\":25.2,\"longitude\":55.27,"
                                + "\"metric\":\"HUMIDITY\",\"threshold\":35,\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluateIsAdminOnly() throws Exception {
        mvc.perform(post("/api/alerts/admin/evaluate").with(jwt().jwt(j -> j.subject("riya"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAlertAndStreamStart() throws Exception {
        mvc.perform(post("/api/alerts/test").with(jwt().jwt(j -> j.subject("kabir"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEST"));

        mvc.perform(get("/api/alerts/stream").with(jwt().jwt(j -> j.subject("kabir"))).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }
}
