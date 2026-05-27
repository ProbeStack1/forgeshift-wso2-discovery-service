package com.forgeshift.wso2discovery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigins;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final String exposedHeaders;
    private final long maxAgeSeconds;

    public CorsConfig(
            @Value("${forgeshift.cors.allowed-origins:*}") String allowedOrigins,
            @Value("${forgeshift.cors.allowed-methods:*}") String allowedMethods,
            @Value("${forgeshift.cors.allowed-headers:*}") String allowedHeaders,
            @Value("${forgeshift.cors.exposed-headers:*}") String exposedHeaders,
            @Value("${forgeshift.cors.max-age-seconds:3600}") long maxAgeSeconds) {
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.exposedHeaders = exposedHeaders;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(csv(allowedOrigins))
                .allowedMethods(csv(allowedMethods))
                .allowedHeaders(csv(allowedHeaders))
                .exposedHeaders(csv(exposedHeaders))
                .allowCredentials(false)
                .maxAge(maxAgeSeconds);
    }

    private String[] csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toArray(String[]::new);
    }
}
