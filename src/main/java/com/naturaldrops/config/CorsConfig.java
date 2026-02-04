package com.naturaldrops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Production-safe origins:
        // - Local dev web (Expo web / React Native web)
        // - Vercel preview + production deployments
        // Note: Native Expo Go apps do not send Origin headers, so CORS doesn't apply.
        List<String> allowedOriginPatterns = Arrays.asList(
                "http://localhost:8081",
                "https://*.vercel.app"
        );
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        
        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow all methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Allow credentials (cookies / auth headers)
        config.setAllowCredentials(true);
        
        // Max age for preflight cache (1 hour)
        config.setMaxAge(3600L);
        
        // Expose headers for mobile clients
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));
        
        source.registerCorsConfiguration("/api/**", config);
        
        return new CorsFilter(source);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}

