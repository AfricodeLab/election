package com.brandedhustlers.electionbackend.common.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.List;

@EnableWebMvc
@Configuration
public class WebConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String originsEnv = System.getenv("CORS_ALLOWED_ORIGINS");

        // Fall back to a permissive wildcard when the env-var is absent so the
        // app starts cleanly on Heroku even before CORS_ALLOWED_ORIGINS is set.
        // In production always set CORS_ALLOWED_ORIGINS to your frontend domains.
        List<String> allowedOrigins = (originsEnv != null && !originsEnv.isBlank())
                ? Arrays.asList(originsEnv.split(","))
                : List.of("*");

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "ngrok-skip-browser-warning"));

        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"));

        if (allowedOrigins.contains("*")) {
            // allowCredentials=true is incompatible with wildcard origin in the
            // CORS spec — disable credentials when falling back to wildcard.
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowCredentials(true);
        } else {
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowCredentials(true);
        }

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
