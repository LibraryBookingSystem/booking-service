package com.library.booking_service.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for booking ownership checks
 * Validates that the booking belongs to the authenticated user
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresBookingOwnership {
    String bookingIdParam() default "id";
    boolean adminBypass() default true;
}

