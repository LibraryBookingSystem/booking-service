package com.library.booking_service.dto;

import java.time.LocalDateTime;

/**
 * DTO for updating an existing booking
 */
public class UpdateBookingRequest {
    
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    // Getters and Setters
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}

