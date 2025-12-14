package com.library.booking_service.exception;

/**
 * Exception thrown when a resource is unavailable for booking
 */
public class ResourceUnavailableException extends RuntimeException {
    
    public ResourceUnavailableException(String message) {
        super(message);
    }
}





