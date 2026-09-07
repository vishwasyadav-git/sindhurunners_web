package com.sindhueventpay.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration.
 *
 * <p>Allowed origins are read from {@code app.cors.allowed-origins}
 * (comma-separated). In production this must be the exact frontend domain(s).
 * Wildcard ("*") is intentionally not permitted.
 *
 * <p>Only the methods required by the registration/payment API are allowed.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Autowired
    private AppProperties appProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = appProperties.getCors().getAllowedOrigins().split(",");

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(
                        "Content-Type",
                        "Authorization",
                        "X-Requested-With",
                        "X-Correlation-Id",
                        "X-Razorpay-Signature")
                .exposedHeaders("X-Correlation-Id")
                .allowCredentials(false)
                .maxAge(3600); // pre-flight cache: 1 hour
    }
}
