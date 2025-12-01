package com.library.booking_service.exception;

/**
 * Exception thrown when check-in is invalid
 */
public class InvalidCheckInException extends RuntimeException {
    
    public InvalidCheckInException(String message) {
        super(message);
    }
}

