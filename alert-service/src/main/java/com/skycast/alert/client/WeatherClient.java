package com.skycast.alert.client;

import com.skycast.common.dto.CurrentWeatherDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative HTTP client (OpenFeign). "weather-service" is resolved through Eureka + Spring Cloud
 * LoadBalancer; calls are wrapped in a Resilience4j circuit breaker with the fallback below.
 */
@FeignClient(name = "weather-service", fallbackFactory = WeatherClientFallbackFactory.class)
public interface WeatherClient {

    @GetMapping("/api/weather/current")
    CurrentWeatherDto current(@RequestParam("lat") double lat,
                              @RequestParam("lon") double lon,
                              @RequestParam("name") String name);
}
