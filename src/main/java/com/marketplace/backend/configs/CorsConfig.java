package com.marketplace.backend.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "https://orderly-ui.vercel.app",
                "http://localhost:5173",
                "http://localhost:5174"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE","OPTIONS","PATCH"
        ));

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Request-Id",
                "Idempotency-Key"
        ));

        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of(
                "X-Request-Id"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // return new CorsFilter(source);
       return source;
    }
}
