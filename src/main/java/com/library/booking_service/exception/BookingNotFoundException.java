package com.library.booking_service.exception;

/**
 * Exception thrown when a booking is not found
 */
public class BookingNotFoundException extends RuntimeException {
    
    public BookingNotFoundException(String message) {
        super(message);
    }
}





