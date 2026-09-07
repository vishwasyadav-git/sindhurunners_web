package com.sindhueventpay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Root application configuration class.
 * Registers {@link AppProperties} so it is bound and validated at startup.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
    // Intentionally empty — registration of AppProperties is the sole purpose.
}
