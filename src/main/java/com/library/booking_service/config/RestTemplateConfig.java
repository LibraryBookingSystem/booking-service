package com.library.booking_service.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;

/**
 * Configuration for RestTemplate to communicate with other services
 * Includes interceptor to forward JWT token for inter-service authentication
 */
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        
        // Add interceptor to forward Authorization header
        restTemplate.setInterceptors(Collections.singletonList(new AuthForwardingInterceptor()));
        
        return restTemplate;
    }
}

