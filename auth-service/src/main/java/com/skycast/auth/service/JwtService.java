package com.skycast.auth.service;

import com.skycast.auth.domain.AppUser;
import com.skycast.common.dto.AuthResponse;
import com.skycast.common.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/** Issues HS256 JWTs with jjwt. Every resource server verifies them with the same secret (weather-common). */
@Service
public class JwtService {

    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    public AuthResponse issue(AppUser user) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.expirationMinutes(), ChronoUnit.MINUTES);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();

        String token = Jwts.builder()
                .subject(user.getUsername())
                .issuer(props.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .claim("uid", user.getId())
                .claim("roles", roles)
                .signWith(props.secretKey(), Jwts.SIG.HS256)
                .compact();

        return new AuthResponse(token, user.getUsername(), roles,
                LocalDateTime.ofInstant(expires, ZoneId.systemDefault()));
    }
}
