package com.library.booking_service.dto;

import com.library.booking_service.entity.Booking;
import com.library.booking_service.entity.BookingStatus;
import java.time.LocalDateTime;

/**
 * DTO for booking response
 */
public class BookingResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long resourceId;
    private String resourceName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status;
    private String qrCode;
    private LocalDateTime checkedInAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public BookingResponse() {
    }

    public BookingResponse(Long id, Long userId, String userName, String userEmail, Long resourceId,
            String resourceName, LocalDateTime startTime,
            LocalDateTime endTime, BookingStatus status, String qrCode,
            LocalDateTime checkedInAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.qrCode = qrCode;
        this.checkedInAt = checkedInAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Convert Booking entity to BookingResponse DTO
     * Note: resourceName should be fetched separately and passed to
     * fromBookingWithResourceName
     */
    public static BookingResponse fromBooking(Booking booking) {
        return fromBooking(booking, null, null, null);
    }

    /**
     * Convert Booking entity to BookingResponse DTO with resource name
     */
    public static BookingResponse fromBooking(Booking booking, String resourceName) {
        return fromBooking(booking, resourceName, null, null);
    }

    /**
     * Convert Booking entity to BookingResponse DTO with resource name and user
     * info
     */
    public static BookingResponse fromBooking(Booking booking, String resourceName, String userName, String userEmail) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                userName,
                userEmail,
                booking.getResourceId(),
                resourceName, // resourceName passed as parameter
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getQrCode(),
                booking.getCheckedInAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    /**
     * Convert Booking entity to BookingResponse DTO with resource name
     */
    public static BookingResponse fromBookingWithResourceName(Booking booking, String resourceName) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                null,  // userName - not available in this context
                null,  // userEmail - not available in this context
                booking.getResourceId(),
                resourceName,
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getQrCode(),
                booking.getCheckedInAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

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

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
