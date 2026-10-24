package com.skycast.weather.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/** Classic XML bean definitions living next to annotation config (see resources/beans.xml). */
@Configuration
@ImportResource("classpath:beans.xml")
public class XmlBeansConfig {
}
