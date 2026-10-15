package com.jobtrackr.jobtrackrbackend.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

// Lets the Angular app (a different origin: another port locally, another domain when deployed) call the API.
// SecurityConfig enables it with http.cors(...), which picks up this CorsConfigurationSource bean. It has to
// live in the security filter chain: a browser's preflight OPTIONS request carries no token, so without this
// the chain would reject it with 401 before Spring MVC's own CORS handling ever ran.
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Without this the browser hides the Location header of a 201 from JavaScript
        configuration.setExposedHeaders(List.of("Location"));
        // Needed for the refresh-token cookie: without it the browser neither stores the cookie from a
        // cross-origin response nor sends it back. Allowed because the origins are an explicit list
        // (credentials can never be combined with "*"). The access token still travels in a header.
        configuration.setAllowCredentials(true);
        // Browsers may cache a successful preflight for this long instead of repeating it before every call
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
