package com.skycast.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills @CreatedDate / @LastModifiedDate columns automatically. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
