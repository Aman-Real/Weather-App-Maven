package com.skycast.auth.service;

import com.skycast.auth.domain.AppUser;
import com.skycast.auth.domain.Role;
import com.skycast.auth.repository.UserRepository;
import com.skycast.common.dto.AuthRequest;
import com.skycast.common.dto.AuthResponse;
import com.skycast.common.dto.RegisterRequest;
import com.skycast.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        AppUser user = users.save(new AppUser(req.username().trim(), req.email().trim().toLowerCase(),
                encoder.encode(req.password()), EnumSet.of(Role.USER)));
        return jwtService.issue(user);
    }

    /** Throws BadCredentialsException (-> 401) when the password is wrong. */
    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest req) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        AppUser user = users.findByUsername(req.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return jwtService.issue(user);
    }
}
