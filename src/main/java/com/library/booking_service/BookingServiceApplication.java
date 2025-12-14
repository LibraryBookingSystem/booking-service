package com.library.booking_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for Booking Service
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.library.booking_service", "com.library.common"})
public class BookingServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}





