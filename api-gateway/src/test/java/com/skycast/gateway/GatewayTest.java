package com.skycast.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;

/** Boots the reactive gateway on a random port (no Eureka, so lb:// routes have no instances). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=true"})
@AutoConfigureWebTestClient
class GatewayTest {

    @Autowired
    private WebTestClient web;

    @Value("${skycast.jwt.secret}")
    private String secret;

    @Test
    void servesThePwa() {
        web.get().uri("/").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(containsString("SkyCast"));
        web.get().uri("/manifest.json").exchange().expectStatus().isOk();
        web.get().uri("/sw.js").exchange().expectStatus().isOk();
    }

    @Test
    void protectedRoutesNeedAToken() {
        web.get().uri("/api/alerts").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/alerts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenPassesAndCircuitBreakerFallsBack() {
        web.get().uri("/api/alerts/rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").value(containsString("alert-service"));
    }

    @Test
    void functionalEndpoints() {
        web.get().uri("/api/system/ping").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        web.post().uri("/fallback/weather-service").exchange().expectStatus().isEqualTo(503);
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void actuatorDetailsNeedAdmin() {
        web.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                .exchange().expectStatus().isForbidden();
        web.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk();
    }

    private String token(String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("tester").issuer("skycast-auth")
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
