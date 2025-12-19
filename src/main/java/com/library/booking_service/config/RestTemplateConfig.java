package com.library.booking_service.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
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
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new TokenForwardingInterceptor());
        restTemplate.setInterceptors(interceptors);
        
        return restTemplate;
    }
    
    /**
     * Interceptor to forward Authorization header from incoming request to outgoing service calls
     */
    private static class TokenForwardingInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws java.io.IOException {
            
            // Get current request context
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest httpRequest = attributes.getRequest();
                String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
                
                // Forward Authorization header if present
                if (authHeader != null && !authHeader.isEmpty()) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
                }
            }
            
            return execution.execute(request, body);
        }
    }
}

