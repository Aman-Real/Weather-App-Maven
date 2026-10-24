package com.skycast.gateway;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/** WebFlux functional endpoints (RouterFunction + handler lambdas returning Mono<ServerResponse>). */
@Configuration
public class FallbackRouter {

    /** Circuit-breaker fallbacks: any method, a friendly 503 JSON instead of a gateway error page. */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoutes() {
        return route(path("/fallback/{service}"), request -> ServerResponse
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "status", 503,
                        "error", "Service Unavailable",
                        "message", request.pathVariable("service") + " is temporarily unavailable, please retry shortly",
                        "path", request.path(),
                        "timestamp", LocalDateTime.now().toString())));
    }

    /** Which services are registered in Eureka right now (reactive: Flux<String> collected into a Mono). */
    @Bean
    public RouterFunction<ServerResponse> systemRoutes(ReactiveDiscoveryClient discovery) {
        return route(GET("/api/system/services"), request -> discovery.getServices()
                .collectSortedList()
                .flatMap(services -> ServerResponse.ok().bodyValue(Map.of("services", services))))
                .andRoute(GET("/api/system/ping"), request -> ServerResponse.ok()
                        .body(Mono.fromSupplier(() -> Map.of("status", "UP", "time", LocalDateTime.now().toString())), Map.class));
    }
}
