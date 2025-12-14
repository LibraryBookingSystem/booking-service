package com.library.booking_service.security;

import com.library.common.security.BaseJwtAuthenticationFilter;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * JWT Authentication Filter for Booking Service.
 * Extends common BaseJwtAuthenticationFilter.
 */
@Component
public class JwtAuthenticationFilter extends BaseJwtAuthenticationFilter {

    @Override
    protected Set<String> getPublicEndpoints() {
        return Set.of(
            "/api/health",
            "/api/bookings/health"
        );
    }
}
