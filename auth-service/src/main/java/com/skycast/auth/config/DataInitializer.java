package com.skycast.auth.config;

import com.skycast.auth.domain.AppUser;
import com.skycast.auth.domain.Role;
import com.skycast.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/** Seeds the demo accounts once (admin password comes from ADMIN_PASSWORD in real deployments). */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String demoPassword;

    public DataInitializer(UserRepository users, PasswordEncoder encoder,
                           @Value("${skycast.seed.admin-password:admin123}") String adminPassword,
                           @Value("${skycast.seed.demo-password:demo123}") String demoPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!users.existsByUsernameIgnoreCase("admin")) {
            users.save(new AppUser("admin", "admin@skycast.local", encoder.encode(adminPassword), EnumSet.of(Role.ADMIN, Role.USER)));
            log.info("Seeded user 'admin'");
        }
        if (!users.existsByUsernameIgnoreCase("demo")) {
            users.save(new AppUser("demo", "demo@skycast.local", encoder.encode(demoPassword), EnumSet.of(Role.USER)));
            log.info("Seeded user 'demo'");
        }
    }
}
