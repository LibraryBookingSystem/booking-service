package com.library.booking_service.controller;

import com.library.booking_service.dto.BookingResponse;
import com.library.booking_service.dto.CheckInRequest;
import com.library.booking_service.dto.CreateBookingRequest;
import com.library.booking_service.dto.UpdateBookingRequest;
import com.library.booking_service.security.annotation.RequiresBookingOwnership;
import com.library.common.security.annotation.RequiresRole;
import com.library.booking_service.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for booking management endpoints
 * Uses AOP annotations for RBAC authorization
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    
    private final BookingService bookingService;
    
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }
    
    /**
     * Create a new booking
     * POST /api/bookings
     * Authorization: AUTHENTICATED
     * userId is extracted from JWT token
     */
    @PostMapping
    @RequiresRole // Any authenticated user
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        BookingResponse response = bookingService.createBooking(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all bookings
     * GET /api/bookings
     * Authorization: ADMIN only
     */
    @GetMapping
    @RequiresRole({"ADMIN"})
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        List<BookingResponse> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Get booking by ID
     * GET /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can view their own bookings, Admins can view any
     */
    @GetMapping("/{id}")
    @RequiresRole
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get bookings by user ID
     * GET /api/bookings/user/{userId}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only view their own bookings, Admins can view any
     */
    @GetMapping("/user/{userId}")
    @RequiresRole
    public ResponseEntity<List<BookingResponse>> getBookingsByUserId(
            @PathVariable Long userId,
            HttpServletRequest request) {
        Long authenticatedUserId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");
        
        // Check ownership: users can only view their own bookings, admins can view any
        if (!authenticatedUserId.equals(userId) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<BookingResponse> bookings = bookingService.getBookingsByUserId(userId);
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Get bookings by resource ID
     * GET /api/bookings/resource/{resourceId}
     * Authorization: AUTHENTICATED (any role can see resource bookings)
     */
    @GetMapping("/resource/{resourceId}")
    @RequiresRole
    public ResponseEntity<List<BookingResponse>> getBookingsByResourceId(@PathVariable Long resourceId) {
        List<BookingResponse> bookings = bookingService.getBookingsByResourceId(resourceId);
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Update booking
     * PUT /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only update their own bookings, Admins can update any
     */
    /**
     * Update booking
     * PUT /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only update their own bookings, Admins can update any
     */
    @PutMapping("/{id}")
    @RequiresRole
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        BookingResponse response = bookingService.updateBooking(id, userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancel booking
     * DELETE /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only cancel their own bookings, Admins can cancel any
     */
    @DeleteMapping("/{id}")
    @RequiresRole
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("userRole");
        bookingService.cancelBooking(id, userId, role);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Check-in to booking
     * POST /api/bookings/checkin
     * Authorization: AUTHENTICATED
     * Resource Ownership: Validated by service (QR code must belong to user)
     */
    @PostMapping("/checkin")
    @RequiresRole
    public ResponseEntity<BookingResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("userRole");
        BookingResponse response = bookingService.checkIn(request.getQrCode(), userId, role);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get currently booked resource IDs (active bookings)
     * GET /api/bookings/booked-resources
     * Authorization: AUTHENTICATED (any role can see which resources are booked)
     * Returns list of resource IDs that have active bookings (CONFIRMED or CHECKED_IN)
     */
    @GetMapping("/booked-resources")
    @RequiresRole
    public ResponseEntity<List<Long>> getBookedResourceIds() {
        List<Long> bookedResourceIds = bookingService.getBookedResourceIds();
        return ResponseEntity.ok(bookedResourceIds);
    }
    
    /**
     * Health check endpoint
     * GET /api/bookings/health
     * Authorization: PUBLIC
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Booking Service is running!");
    }
}
