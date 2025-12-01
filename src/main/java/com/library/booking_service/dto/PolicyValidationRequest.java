package com.library.booking_service.dto;

import java.time.LocalDateTime;

/**
 * DTO for policy validation request (sent to Policy Service)
 */
public class PolicyValidationRequest {
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long userId;
    private Integer currentBookingCount;
    
    public PolicyValidationRequest() {}
    
    public PolicyValidationRequest(LocalDateTime startTime, LocalDateTime endTime, Long userId, Integer currentBookingCount) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.userId = userId;
        this.currentBookingCount = currentBookingCount;
    }
    
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
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getCurrentBookingCount() {
        return currentBookingCount;
    }
    
    public void setCurrentBookingCount(Integer currentBookingCount) {
        this.currentBookingCount = currentBookingCount;
    }
}

