package com.skycast.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Routes are declared in Java (Gateway Java DSL). lb://name = look the service up in Eureka and
 * load-balance across its instances. Normal API routes get a Resilience4j circuit breaker with a
 * JSON fallback; the long-lived SSE stream and the slow batch endpoint deliberately do not.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator skycastRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("alerts-stream", r -> r.order(-10)
                        .path("/api/alerts/stream")
                        .uri("lb://alert-service"))
                .route("weather-admin", r -> r.order(-10)
                        .path("/api/weather/admin/**")
                        .uri("lb://weather-service"))
                .route("auth", r -> r
                        .path("/api/auth/**", "/api/users/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("authCB").setFallbackUri("forward:/fallback/auth-service")))
                        .uri("lb://auth-service"))
                .route("weather", r -> r
                        .path("/api/weather/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("weatherCB").setFallbackUri("forward:/fallback/weather-service")))
                        .uri("lb://weather-service"))
                .route("alerts", r -> r
                        .path("/api/alerts/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("alertsCB").setFallbackUri("forward:/fallback/alert-service")))
                        .uri("lb://alert-service"))
                .build();
    }

    /** Reactive circuit breakers: 15 s per call (history archive calls can be slow), open after 50% failures. */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> gatewayCircuitBreakers() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build())
                .build());
    }
}
