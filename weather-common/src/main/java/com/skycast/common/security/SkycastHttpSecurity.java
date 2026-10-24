package com.skycast.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Shared stateless JWT resource-server setup. Each service adds its own URL rules:
 * <pre>skycast.apply(http).authorizeHttpRequests(a -> ...).build()</pre>
 */
public class SkycastHttpSecurity {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final BearerTokenResolver bearerTokenResolver;

    public SkycastHttpSecurity(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter,
                               BearerTokenResolver bearerTokenResolver) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.bearerTokenResolver = bearerTokenResolver;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));
    }
}
