package com.library.booking_service.service;

import com.library.booking_service.dto.*;
import com.library.booking_service.entity.Booking;
import com.library.booking_service.entity.BookingStatus;
import com.library.booking_service.exception.BookingNotFoundException;
import com.library.booking_service.exception.InvalidCheckInException;
import com.library.booking_service.exception.ResourceUnavailableException;
import com.library.booking_service.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for booking operations
 */
@Service
public class BookingService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);
    
    private final BookingRepository bookingRepository;
    private final RestTemplate restTemplate;
    private final BookingEventPublisher eventPublisher;
    
    @Value("${policy-service-url:http://localhost:3005}")
    private String policyServiceUrl;
    
    @Value("${catalog-service-url:http://localhost:3003}")
    private String catalogServiceUrl;
    
    @Value("${user-service-url:http://localhost:3001}")
    private String userServiceUrl;
    
    public BookingService(BookingRepository bookingRepository,
                          RestTemplate restTemplate,
                          BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Create a new booking
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request) {
        logger.info("Creating booking for user: {} and resource: {}", userId, request.getResourceId());
        
        try {
            // 1. Verify user is not restricted
            verifyUserNotRestricted(userId);
            
            // 2. Verify resource exists and is available
            verifyResourceAvailable(request.getResourceId());
            
            // 3. Check for overlapping bookings
            List<Booking> overlapping = bookingRepository.findOverlappingBookings(
                request.getResourceId(), request.getStartTime(), request.getEndTime());
            if (!overlapping.isEmpty()) {
                throw new ResourceUnavailableException(
                    "Resource is already booked for the requested time slot");
            }
            
            // 4. Get current booking count for user
            int currentBookingCount = bookingRepository.findActiveBookingsByUserId(userId).size();
            
            // 5. Validate against policies
            PolicyValidationRequest policyRequest = new PolicyValidationRequest(
                request.getStartTime(),
                request.getEndTime(),
                userId,
                currentBookingCount
            );
            
            PolicyValidationResponse policyValidation = validateWithPolicyService(policyRequest);
            if (!policyValidation.isValid()) {
                throw new ResourceUnavailableException(
                    "Booking violates policy: " + String.join(", ", policyValidation.getViolations()));
            }
            
            // 6. Create booking
            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setResourceId(request.getResourceId());
            booking.setStartTime(request.getStartTime());
            booking.setEndTime(request.getEndTime());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setQrCode(generateQRCode());
            
            booking = bookingRepository.save(booking);
            logger.info("Booking created successfully: {} (ID: {})", booking.getQrCode(), booking.getId());
            
            BookingResponse response = BookingResponse.fromBooking(booking);
            eventPublisher.publishBookingCreated(response);
            
            return response;
        } catch (ResourceUnavailableException e) {
            logger.error("Resource unavailable: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating booking: {}", e.getMessage(), e);
            throw new ResourceUnavailableException("Failed to create booking: " + e.getMessage());
        }
    }
    
    /**
     * Get all bookings
     */
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
            .map(BookingResponse::fromBooking)
            .collect(Collectors.toList());
    }
    
    /**
     * Get booking by ID
     */
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + id));
        return BookingResponse.fromBooking(booking);
    }
    
    /**
     * Get bookings by user ID
     */
    public List<BookingResponse> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
            .map(BookingResponse::fromBooking)
            .collect(Collectors.toList());
    }
    
    /**
     * Get bookings by resource ID
     */
    public List<BookingResponse> getBookingsByResourceId(Long resourceId) {
        return bookingRepository.findByResourceId(resourceId).stream()
            .map(BookingResponse::fromBooking)
            .collect(Collectors.toList());
    }
    
    /**
     * Update booking
     */
    @Transactional
    public BookingResponse updateBooking(Long id, Long userId, UpdateBookingRequest request) {
        logger.info("Updating booking: {}", id);
        
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + id));
        
        // Verify ownership
        if (!booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException("Booking not found with id: " + id);
        }
        
        // Can only update CONFIRMED bookings
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResourceUnavailableException("Only confirmed bookings can be modified");
        }
        
        LocalDateTime newStartTime = request.getStartTime() != null ? request.getStartTime() : booking.getStartTime();
        LocalDateTime newEndTime = request.getEndTime() != null ? request.getEndTime() : booking.getEndTime();
        
        // Check for overlapping bookings (excluding current booking)
        List<Booking> overlapping = bookingRepository.findOverlappingBookings(
            booking.getResourceId(), newStartTime, newEndTime);
        overlapping.removeIf(b -> b.getId().equals(id));
        
        if (!overlapping.isEmpty()) {
            throw new ResourceUnavailableException(
                "Resource is already booked for the requested time slot");
        }
        
        // Validate with policy service
        int currentBookingCount = bookingRepository.findActiveBookingsByUserId(userId).size();
        PolicyValidationRequest policyRequest = new PolicyValidationRequest(
            newStartTime, newEndTime, userId, currentBookingCount);
        
        PolicyValidationResponse policyValidation = validateWithPolicyService(policyRequest);
        if (!policyValidation.isValid()) {
            throw new ResourceUnavailableException(
                "Booking violates policy: " + String.join(", ", policyValidation.getViolations()));
        }
        
        booking.setStartTime(newStartTime);
        booking.setEndTime(newEndTime);
        booking = bookingRepository.save(booking);
        
        logger.info("Booking updated successfully: {} (ID: {})", booking.getQrCode(), booking.getId());
        
        BookingResponse response = BookingResponse.fromBooking(booking);
        eventPublisher.publishBookingUpdated(response);
        
        return response;
    }
    
    /**
     * Cancel booking
     */
    @Transactional
    public void cancelBooking(Long id, Long userId) {
        logger.info("Canceling booking: {}", id);
        
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + id));
        
        // Verify ownership
        if (!booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException("Booking not found with id: " + id);
        }
        
        // Can only cancel CONFIRMED or PENDING bookings
        if (booking.getStatus() != BookingStatus.CONFIRMED && 
            booking.getStatus() != BookingStatus.PENDING) {
            throw new ResourceUnavailableException("Only confirmed or pending bookings can be canceled");
        }
        
        booking.setStatus(BookingStatus.CANCELED);
        bookingRepository.save(booking);
        
        logger.info("Booking canceled successfully: {} (ID: {})", booking.getQrCode(), id);
        
        eventPublisher.publishBookingCanceled(BookingResponse.fromBooking(booking));
    }
    
    /**
     * Check-in to booking using QR code
     */
    @Transactional
    public BookingResponse checkIn(String qrCode) {
        logger.info("Processing check-in for QR code: {}", qrCode);
        
        Booking booking = bookingRepository.findByQrCode(qrCode)
            .orElseThrow(() -> new InvalidCheckInException("Invalid QR code"));
        
        // Validate check-in
        LocalDateTime now = LocalDateTime.now();
        
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidCheckInException(
                "Booking is not in CONFIRMED status. Current status: " + booking.getStatus());
        }
        
        if (now.isBefore(booking.getStartTime())) {
            throw new InvalidCheckInException("Check-in is too early. Booking starts at: " + booking.getStartTime());
        }
        
        // Check if within grace period (this would come from policy service, but for now use a default)
        // In production, fetch grace period from policy service
        LocalDateTime gracePeriodEnd = booking.getStartTime().plusMinutes(15); // Default 15 minutes
        if (now.isAfter(gracePeriodEnd)) {
            throw new InvalidCheckInException("Check-in window has expired. Grace period ended at: " + gracePeriodEnd);
        }
        
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(now);
        booking = bookingRepository.save(booking);
        
        logger.info("Check-in successful for booking: {} (ID: {})", booking.getQrCode(), booking.getId());
        
        BookingResponse response = BookingResponse.fromBooking(booking);
        eventPublisher.publishBookingCheckedIn(response);
        
        return response;
    }
    
    /**
     * Mark no-show bookings (called by scheduler)
     */
    @Transactional
    public void processNoShows(int gracePeriodMinutes) {
        logger.info("Processing no-show bookings");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime gracePeriodStart = now.minusMinutes(gracePeriodMinutes);
        
        List<Booking> noShowBookings = bookingRepository.findNoShowBookings(now, gracePeriodStart);
        
        for (Booking booking : noShowBookings) {
            booking.setStatus(BookingStatus.NO_SHOW);
            bookingRepository.save(booking);
            
            logger.info("Marked booking as no-show: {} (ID: {})", booking.getQrCode(), booking.getId());
            
            eventPublisher.publishBookingNoShow(BookingResponse.fromBooking(booking));
        }
        
        logger.info("Processed {} no-show bookings", noShowBookings.size());
    }
    
    /**
     * Verify user is not restricted
     */
    private void verifyUserNotRestricted(Long userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId + "/restricted";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Boolean restricted = (Boolean) response.getBody().get("restricted");
                if (Boolean.TRUE.equals(restricted)) {
                    throw new ResourceUnavailableException("User account is restricted");
                }
            }
        } catch (RestClientException e) {
            logger.warn("Failed to verify user status: {}", e.getMessage());
            // Continue - don't block booking if user service is unavailable
        }
    }
    
    /**
     * Verify resource exists and is available
     */
    private void verifyResourceAvailable(Long resourceId) {
        try {
            String url = catalogServiceUrl + "/api/resources/" + resourceId;
            logger.debug("Checking resource availability at: {}", url);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                logger.error("Resource check returned status: {} for resourceId: {}", response.getStatusCode(), resourceId);
                throw new ResourceUnavailableException("Resource not found: " + resourceId);
            }
            
            logger.debug("Resource {} is available", resourceId);
            // Could also check resource status here if needed
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            logger.error("Resource not found: {} - {}", resourceId, e.getMessage());
            throw new ResourceUnavailableException("Resource not found: " + resourceId);
        } catch (RestClientException e) {
            logger.error("Failed to verify resource {}: {} - Error: {}", resourceId, e.getMessage(), e.getClass().getName(), e);
            throw new ResourceUnavailableException("Failed to verify resource availability: " + e.getMessage());
        }
    }
    
    /**
     * Validate booking with Policy Service
     */
    private PolicyValidationResponse validateWithPolicyService(PolicyValidationRequest request) {
        try {
            String url = policyServiceUrl + "/api/policies/validate";
            logger.debug("Validating booking with policy service at: {}", url);
            
            ResponseEntity<PolicyValidationResponse> response = restTemplate.postForEntity(
                url, request, PolicyValidationResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Policy validation result: valid={}", response.getBody().isValid());
                return response.getBody();
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            logger.warn("Policy service unavailable (connection failed): {} - Continuing with validation passed", e.getMessage());
            // Return valid if policy service is unavailable (fail open)
            PolicyValidationResponse validation = new PolicyValidationResponse();
            validation.setValid(true);
            return validation;
        } catch (RestClientException e) {
            logger.warn("Failed to validate with policy service: {} - Continuing with validation passed", e.getMessage());
            // Return valid if policy service is unavailable (fail open)
            PolicyValidationResponse validation = new PolicyValidationResponse();
            validation.setValid(true);
            return validation;
        }
        
        // Default to valid if validation fails
        logger.warn("Policy validation returned no result, defaulting to valid");
        PolicyValidationResponse validation = new PolicyValidationResponse();
        validation.setValid(true);
        return validation;
    }
    
    /**
     * Generate unique QR code
     */
    private String generateQRCode() {
        return "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

