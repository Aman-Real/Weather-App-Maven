package com.skycast.weather.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Spring Cache abstraction; provider = Caffeine (see spring.cache.* in application.yml). */
@Configuration
@EnableCaching
public class CachingConfig {
}
