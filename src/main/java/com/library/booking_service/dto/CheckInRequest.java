package com.library.booking_service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for check-in request
 */
public class CheckInRequest {
    
    @NotBlank(message = "QR code is required")
    private String qrCode;
    
    // Getters and Setters
    public String getQrCode() {
        return qrCode;
    }
    
    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }
}





