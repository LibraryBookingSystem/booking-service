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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);
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
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        // Extract Authorization header to forward to other services
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            // Try case-insensitive header names
            authHeader = httpRequest.getHeader("authorization");
        }
        BookingResponse response = bookingService.createBooking(userId, request, authHeader);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get all bookings
     * GET /api/bookings
     * Authorization: ADMIN only
     */
    @GetMapping
    @RequiresRole({ "ADMIN" })
    public ResponseEntity<List<BookingResponse>> getAllBookings(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = httpRequest.getHeader("authorization");
        }
        List<BookingResponse> bookings = bookingService.getAllBookings(authHeader);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Get booking by ID
     * GET /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can view their own bookings, Admins can view any
     */
    @GetMapping("/{id}")
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = httpRequest.getHeader("authorization");
        }
        BookingResponse response = bookingService.getBookingById(id, authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * Get bookings by user ID
     * GET /api/bookings/user/{userId}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only view their own bookings, Faculty/Admins can view any
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByUserId(
            @PathVariable Long userId,
            HttpServletRequest request) {
        Long authenticatedUserId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");

        // Check ownership: users can only view their own bookings, faculty/admins can view any
        if (!authenticatedUserId.equals(userId) && !"ADMIN".equals(role) && !"FACULTY".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = request.getHeader("authorization");
        }
        List<BookingResponse> bookings = bookingService.getBookingsByUserId(userId, authHeader);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Get bookings by resource ID
     * GET /api/bookings/resource/{resourceId}
     * Authorization: AUTHENTICATED (any role can see resource bookings)
     */
    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByResourceId(
            @PathVariable Long resourceId,
            HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = httpRequest.getHeader("authorization");
        }
        List<BookingResponse> bookings = bookingService.getBookingsByResourceId(resourceId, authHeader);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Update booking
     * PUT /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only update their own bookings, Admins can
     * update any
     */
    /**
     * Update booking
     * PUT /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only update their own bookings, Admins can
     * update any
     */
    @PutMapping("/{id}")
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        // Extract Authorization header to forward to other services
        String authHeader = httpRequest.getHeader("Authorization");
        BookingResponse response = bookingService.updateBooking(id, userId, request, authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel booking
     * DELETE /api/bookings/{id}
     * Authorization: AUTHENTICATED
     * Resource Ownership: Users can only cancel their own bookings, Admins can
     * cancel any
     */
    @DeleteMapping("/{id}")
    @RequiresBookingOwnership(bookingIdParam = "id")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("userRole");
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = httpRequest.getHeader("authorization");
        }
        bookingService.cancelBooking(id, userId, role, authHeader);
        return ResponseEntity.noContent().build();
    }

    /**
     * Check-in to booking
     * POST /api/bookings/checkin
     * Authorization: AUTHENTICATED
     * Resource Ownership: Validated by service (QR code must belong to user)
     */
    @PostMapping("/checkin")
    public ResponseEntity<BookingResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("userRole");
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = httpRequest.getHeader("authorization");
        }
        BookingResponse response = bookingService.checkIn(request.getQrCode(), userId, role, authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * Get currently booked resource IDs (active bookings)
     * GET /api/bookings/booked-resources
     * Authorization: AUTHENTICATED (any role can see which resources are booked)
     * Returns list of resource IDs that have active bookings (CONFIRMED or
     * CHECKED_IN)
     */
    @GetMapping("/booked-resources")
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
