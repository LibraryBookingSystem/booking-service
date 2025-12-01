package com.library.booking_service.controller;

import com.library.booking_service.dto.BookingResponse;
import com.library.booking_service.dto.CheckInRequest;
import com.library.booking_service.dto.CreateBookingRequest;
import com.library.booking_service.dto.UpdateBookingRequest;
import com.library.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for booking management endpoints
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
     * Note: In production, userId would come from JWT token
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody CreateBookingRequest request) {
        // For now, accept userId from header. In production, extract from JWT
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        BookingResponse response = bookingService.createBooking(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all bookings
     * GET /api/bookings
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        List<BookingResponse> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Get booking by ID
     * GET /api/bookings/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get bookings by user ID
     * GET /api/bookings/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByUserId(@PathVariable Long userId) {
        List<BookingResponse> bookings = bookingService.getBookingsByUserId(userId);
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Get bookings by resource ID
     * GET /api/bookings/resource/{resourceId}
     */
    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByResourceId(@PathVariable Long resourceId) {
        List<BookingResponse> bookings = bookingService.getBookingsByResourceId(resourceId);
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Update booking
     * PUT /api/bookings/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody UpdateBookingRequest request) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        BookingResponse response = bookingService.updateBooking(id, userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancel booking
     * DELETE /api/bookings/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        bookingService.cancelBooking(id, userId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Check-in to booking
     * POST /api/bookings/checkin
     */
    @PostMapping("/checkin")
    public ResponseEntity<BookingResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        BookingResponse response = bookingService.checkIn(request.getQrCode());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check endpoint
     * GET /api/bookings/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Booking Service is running!");
    }
}

