package com.skycast.weather.config;

import com.skycast.common.security.SkycastHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Weather data is public; statistics and admin operations need an ADMIN JWT. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SkycastHttpSecurity skycast) throws Exception {
        return skycast.apply(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/weather/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/weather/stats").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/weather/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/status", "/status/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
