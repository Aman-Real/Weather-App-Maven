package com.skycast.alert.config;

import com.skycast.common.security.SkycastHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Every alert endpoint is personal and needs a JWT; /stream also accepts ?access_token= for EventSource. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SkycastHttpSecurity skycast) throws Exception {
        return skycast.apply(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/alerts/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
