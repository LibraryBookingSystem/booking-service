package com.library.booking_service.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * RestTemplate interceptor to forward Authorization header from incoming request
 * to outgoing inter-service calls
 */
public class AuthForwardingInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthForwardingInterceptor.class);
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        
        // Get current HTTP request from RequestContextHolder
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest httpRequest = attributes.getRequest();
            String authHeader = httpRequest.getHeader("Authorization");
            
            if (authHeader != null && !authHeader.isEmpty()) {
                // Forward Authorization header to inter-service call
                request.getHeaders().set("Authorization", authHeader);
                logger.debug("Forwarded Authorization header to inter-service call: {}", request.getURI());
            }
        }
        
        return execution.execute(request, body);
    }
}




