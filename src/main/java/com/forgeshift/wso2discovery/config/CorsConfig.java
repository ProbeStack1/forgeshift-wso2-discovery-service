package com.forgeshift.wso2discovery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS via a servlet {@link CorsFilter} at highest precedence — NOT WebMvcConfigurer.
 * The MVC-level mapping only decorates handler-mapped responses, so error dispatches
 * (500s, non-handler 404s) went out WITHOUT CORS headers and the browser reported them
 * as CORS failures, masking the real error. The filter decorates every response.
 *
 * <p>Origins are applied as PATTERNS: a match echoes the exact request Origin back
 * (never a literal "*"), which works from any localhost port / IP, with or without
 * credentials.</p>
 */
@Configuration
public class CorsConfig {

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

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(csv(allowedOrigins));
        config.setAllowedMethods(csv(allowedMethods));
        config.setAllowedHeaders(csv(allowedHeaders));
        config.setExposedHeaders(csv(exposedHeaders));
        config.setAllowCredentials(true);
        config.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private List<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
