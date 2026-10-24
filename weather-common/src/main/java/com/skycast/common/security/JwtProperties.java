package com.skycast.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * skycast.jwt.* — the HS256 secret is shared by auth-service (signs) and every resource server (verifies).
 * Override with the JWT_SECRET environment variable outside development.
 */
@ConfigurationProperties(prefix = "skycast.jwt")
public record JwtProperties(
        @DefaultValue("dev-only-change-me-skycast-jwt-secret-at-least-32-bytes-long") String secret,
        @DefaultValue("skycast-auth") String issuer,
        @DefaultValue("720") long expirationMinutes) {

    public JwtProperties {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("skycast.jwt.secret must be at least 32 bytes for HS256");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
