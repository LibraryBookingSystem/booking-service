package com.library.booking_service.service;

import com.library.booking_service.dto.*;
import com.library.booking_service.entity.Booking;
import com.library.booking_service.entity.BookingStatus;
import com.library.booking_service.exception.BookingNotFoundException;
import com.library.booking_service.exception.InvalidCheckInException;
import com.library.booking_service.exception.ResourceUnavailableException;
import com.library.common.exception.ForbiddenException;
import com.library.booking_service.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.library.booking_service.scheduler.BookingScheduler;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
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
    private final BookingScheduler bookingScheduler;

    @Value("${policy-service-url:http://localhost:3005}")
    private String policyServiceUrl;

    @Value("${catalog-service-url:http://localhost:3003}")
    private String catalogServiceUrl;

    @Value("${user-service-url:http://localhost:3001}")
    private String userServiceUrl;

    public BookingService(BookingRepository bookingRepository,
            RestTemplate restTemplate,
            BookingEventPublisher eventPublisher,
            @Lazy BookingScheduler bookingScheduler) {
        this.bookingRepository = bookingRepository;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.bookingScheduler = bookingScheduler;
    }

    /**
     * Create a new booking
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request, String authToken) {
        logger.info("Creating booking for user: {} and resource: {}", userId, request.getResourceId());

        try {
            // 1. Verify user is not restricted
            verifyUserNotRestricted(userId, authToken);

            // 2. Verify resource exists and is available
            verifyResourceAvailable(request.getResourceId(), authToken);

            // 3. Check for overlapping bookings
            List<Booking> overlapping = bookingRepository.findOverlappingBookings(
                    request.getResourceId(), request.getStartTime(), request.getEndTime());
            if (!overlapping.isEmpty()) {
                throw new ResourceUnavailableException(
                        "Resource is already booked for the requested time slot");
            }

            // 4. Get current booking count for user (only future/current bookings)
            int currentBookingCount = bookingRepository
                    .findActiveBookingsByUserId(userId, LocalDateTime.now(ZoneOffset.UTC)).size();

            // 5. Validate against policies
            PolicyValidationRequest policyRequest = new PolicyValidationRequest(
                    request.getStartTime(),
                    request.getEndTime(),
                    userId,
                    currentBookingCount);

            PolicyValidationResponse policyValidation = validateWithPolicyService(policyRequest, authToken);
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

            // Schedule event-driven completion task for when booking expires
            bookingScheduler.scheduleBookingCompletion(booking.getId(), booking.getEndTime());

            // Fetch resource name for response
            String resourceName = getResourceName(booking.getResourceId(), authToken);
            BookingResponse response = BookingResponse.fromBooking(booking, resourceName);
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
     * Get all bookings (admin endpoint - includes user info)
     */
    public List<BookingResponse> getAllBookings(String authToken) {
        return bookingRepository.findAll().stream()
                .map(booking -> {
                    String resourceName = getResourceName(booking.getResourceId(), authToken);
                    Map<String, String> userInfo = getUserInfo(booking.getUserId(), authToken);
                    return BookingResponse.fromBooking(booking, resourceName,
                            userInfo.get("name"), userInfo.get("email"));
                })
                .collect(Collectors.toList());
    }

    /**
     * Get booking by ID
     */
    public BookingResponse getBookingById(Long id, String authToken) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + id));
        String resourceName = getResourceName(booking.getResourceId(), authToken);
        return BookingResponse.fromBooking(booking, resourceName);
    }

    /**
     * Get bookings by user ID
     */
    public List<BookingResponse> getBookingsByUserId(Long userId, String authToken) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        logger.info("Found {} bookings for user {}", bookings.size(), userId);

        if (bookings.isEmpty()) {
            logger.warn("No bookings found for user {}", userId);
            return Collections.emptyList();
        }

        // Log each booking found
        for (Booking booking : bookings) {
            logger.debug("Booking {}: user={}, resource={}, status={}, start={}, end={}",
                    booking.getId(), booking.getUserId(), booking.getResourceId(),
                    booking.getStatus(), booking.getStartTime(), booking.getEndTime());
        }

        // Fetch resource names in parallel to avoid sequential timeouts
        List<BookingResponse> responses = bookings.parallelStream()
                .map(booking -> {
                    try {
                        String resourceName = getResourceName(booking.getResourceId(), authToken);
                        BookingResponse response = BookingResponse.fromBooking(booking, resourceName);
                        logger.debug("Created response for booking {}: resourceName={}", booking.getId(), resourceName);
                        return response;
                    } catch (Exception e) {
                        logger.warn("Failed to fetch resource name for booking {}: {}",
                                booking.getId(), e.getMessage());
                        // Return booking with "Unknown Resource" instead of failing completely
                        return BookingResponse.fromBooking(booking, "Unknown Resource");
                    }
                })
                .collect(Collectors.toList());

        logger.info("Returning {} booking responses for user {}", responses.size(), userId);
        return responses;
    }

    /**
     * Get bookings by resource ID
     */
    public List<BookingResponse> getBookingsByResourceId(Long resourceId, String authToken) {
        // Fetch resource name once since all bookings are for the same resource
        String resourceName = getResourceName(resourceId, authToken);
        return bookingRepository.findByResourceId(resourceId).stream()
                .map(booking -> BookingResponse.fromBooking(booking, resourceName))
                .collect(Collectors.toList());
    }

    /**
     * Get list of resource IDs that currently have active bookings
     * Active means CONFIRMED or CHECKED_IN status with current time within booking
     * window
     */
    public List<Long> getBookedResourceIds() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return bookingRepository.findCurrentlyBookedResourceIds(now);
    }

    /**
     * Update booking
     */
    @Transactional
    public BookingResponse updateBooking(Long id, Long userId, UpdateBookingRequest request, String authToken) {
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
        int currentBookingCount = bookingRepository
                .findActiveBookingsByUserId(userId, LocalDateTime.now(ZoneOffset.UTC)).size();
        PolicyValidationRequest policyRequest = new PolicyValidationRequest(
                newStartTime, newEndTime, userId, currentBookingCount);

        PolicyValidationResponse policyValidation = validateWithPolicyService(policyRequest, authToken);
        if (!policyValidation.isValid()) {
            throw new ResourceUnavailableException(
                    "Booking violates policy: " + String.join(", ", policyValidation.getViolations()));
        }

        booking.setStartTime(newStartTime);
        booking.setEndTime(newEndTime);
        booking = bookingRepository.save(booking);

        // Reschedule completion task with new endTime
        bookingScheduler.scheduleBookingCompletion(booking.getId(), booking.getEndTime());

        logger.info("Booking updated successfully: {} (ID: {})", booking.getQrCode(), booking.getId());

        // Fetch resource name for response
        String resourceName = getResourceName(booking.getResourceId(), authToken);
        BookingResponse response = BookingResponse.fromBooking(booking, resourceName);
        eventPublisher.publishBookingUpdated(response);

        return response;
    }

    /**
     * Cancel booking
     */
    @Transactional
    public void cancelBooking(Long id, Long userId, String role, String authToken) {
        logger.info("Canceling booking: {} by user: {} (role: {})", id, userId, role);

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + id));

        // Verify ownership: user must own the booking OR be an admin
        if (!booking.getUserId().equals(userId) && !"ADMIN".equals(role)) {
            throw new BookingNotFoundException("Booking not found with id: " + id);
        }

        // Can only cancel CONFIRMED, PENDING, or CHECKED_IN (ongoing) bookings
        if (booking.getStatus() != BookingStatus.CONFIRMED &&
                booking.getStatus() != BookingStatus.PENDING &&
                booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new ResourceUnavailableException("Only confirmed, pending, or ongoing bookings can be canceled");
        }

        booking.setStatus(BookingStatus.CANCELED);
        bookingRepository.save(booking);

        // Cancel scheduled completion task since booking is canceled
        bookingScheduler.cancelScheduledTask(id);

        logger.info("Booking canceled successfully: {} (ID: {})", booking.getQrCode(), id);

        String resourceName = getResourceName(booking.getResourceId(), authToken);
        eventPublisher.publishBookingCanceled(BookingResponse.fromBooking(booking, resourceName));
    }

    /**
     * Check-in to booking using QR code
     */
    @Transactional
    public BookingResponse checkIn(String qrCode, Long userId, String role, String authToken) {
        logger.info("Processing check-in for QR code: {} by user: {}", qrCode, userId);

        Booking booking = bookingRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new InvalidCheckInException("Invalid QR code"));

        // Validate ownership: user must own the booking OR be a faculty/admin (for
        // manual check-in)
        if (!booking.getUserId().equals(userId) && !"ADMIN".equals(role) && !"FACULTY".equals(role)) {
            throw new ForbiddenException(
                    "You do not have permission to check in to this booking");
        }

        // Validate check-in
        // Use UTC time for comparison since booking times are stored in UTC
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        logger.debug("Check-in time comparison - Current UTC time: {}, Booking start: {}, Booking end: {}",
                now, booking.getStartTime(), booking.getEndTime());

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidCheckInException(
                    "Booking is not in CONFIRMED status. Current status: " + booking.getStatus());
        }

        // Grace Period Definition:
        // Grace period is the time window after the booking start time during which
        // check-in is allowed.
        // It represents the maximum time after startTime that a user can check in to
        // their booking.
        // Example: If grace period is 15 minutes, check-in is allowed from startTime to
        // startTime + 15 minutes.

        // Get grace period from policy service
        int gracePeriodMinutes = getGracePeriodFromPolicy(authToken);
        LocalDateTime gracePeriodEnd = booking.getStartTime().plusMinutes(gracePeriodMinutes);

        // Check if check-in is too early (before booking start time)
        if (now.isBefore(booking.getStartTime())) {
            throw new InvalidCheckInException("Check-in is too early. Booking starts at: " + booking.getStartTime());
        }

        // Enforce grace period: Check-in must be within grace period after start time
        // However, if booking is ongoing (current time between startTime and endTime),
        // allow check-in even if past grace period to support ongoing bookings
        boolean isWithinGracePeriod = !now.isAfter(gracePeriodEnd);
        boolean isOngoingBooking = !now.isBefore(booking.getStartTime()) && !now.isAfter(booking.getEndTime());

        if (!isWithinGracePeriod && !isOngoingBooking) {
            throw new InvalidCheckInException(
                    String.format(
                            "Check-in grace period has expired. Grace period ended at: %s (grace period: %d minutes)",
                            gracePeriodEnd, gracePeriodMinutes));
        }

        // Check if booking has ended
        if (now.isAfter(booking.getEndTime())) {
            throw new InvalidCheckInException("Check-in window has expired. Booking ended at: " + booking.getEndTime());
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(now);
        booking = bookingRepository.save(booking);

        logger.info("Check-in successful for booking: {} (ID: {})", booking.getQrCode(), booking.getId());

        // Fetch resource name for response
        String resourceName = getResourceName(booking.getResourceId(), authToken);
        BookingResponse response = BookingResponse.fromBooking(booking, resourceName);
        eventPublisher.publishBookingCheckedIn(response);

        return response;
    }

    /**
     * Mark no-show bookings (called by scheduler)
     */
    @Transactional
    public void processNoShows(int gracePeriodMinutes, String authToken) {
        logger.info("Processing no-show bookings");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime gracePeriodStart = now.minusMinutes(gracePeriodMinutes);

        List<Booking> noShowBookings = bookingRepository.findNoShowBookings(gracePeriodStart);

        for (Booking booking : noShowBookings) {
            booking.setStatus(BookingStatus.NO_SHOW);
            bookingRepository.save(booking);

            // Cancel any scheduled task since we are handling completion here
            bookingScheduler.cancelScheduledTask(booking.getId());

            logger.info("Marked booking as no-show: {} (ID: {})", booking.getQrCode(), booking.getId());

            String resourceName = getResourceName(booking.getResourceId(), authToken);
            eventPublisher.publishBookingNoShow(BookingResponse.fromBooking(booking, resourceName));
        }

        logger.info("Processed {} no-show bookings", noShowBookings.size());
    }

    /**
     * Complete a specific booking (event-driven, called by scheduler)
     * When endTime is reached:
     * - If user checked in (CHECKED_IN) → set to COMPLETED (successful booking)
     * - If user never checked in (CONFIRMED) → set to CANCELED (no-show)
     * Frees resource if no other active bookings exist
     */
    @Transactional
    public void completeBooking(Long bookingId, String authToken) {
        logger.debug("Processing booking expiration: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElse(null);

        if (booking == null) {
            logger.warn("Booking {} not found for completion", bookingId);
            return;
        }

        // Only process if still in active status
        if (booking.getStatus() != BookingStatus.CONFIRMED &&
                booking.getStatus() != BookingStatus.CHECKED_IN) {
            logger.debug("Booking {} already in status {}, skipping completion",
                    bookingId, booking.getStatus());
            return;
        }

        // Double-check that endTime has passed
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (booking.getEndTime().isAfter(now)) {
            logger.warn("Booking {} endTime {} hasn't passed yet, rescheduling",
                    bookingId, booking.getEndTime());
            // Reschedule for the correct time
            bookingScheduler.scheduleBookingCompletion(bookingId, booking.getEndTime());
            return;
        }

        // Determine final status based on whether user checked in
        boolean userCheckedIn = booking.getStatus() == BookingStatus.CHECKED_IN;

        if (userCheckedIn) {
            // User checked in → successful booking → COMPLETED
            booking.setStatus(BookingStatus.COMPLETED);
            logger.info("Marked booking as completed (user checked in): {} (ID: {}), resourceId: {}, endTime: {}",
                    booking.getQrCode(), booking.getId(), booking.getResourceId(), booking.getEndTime());
        } else {
            // User never checked in → no-show → NO_SHOW
            booking.setStatus(BookingStatus.NO_SHOW);
            logger.info("Marked booking as no-show (user never checked in): {} (ID: {}), resourceId: {}, endTime: {}",
                    booking.getQrCode(), booking.getId(), booking.getResourceId(), booking.getEndTime());
        }

        bookingRepository.save(booking);

        // Check if there are other active bookings for this resource
        List<Long> activeBookings = bookingRepository.findCurrentlyBookedResourceIds(now);
        boolean hasOtherActiveBookings = activeBookings.contains(booking.getResourceId());

        // Only free the resource if there are no other active bookings
        if (!hasOtherActiveBookings) {
            try {
                String resourceName = getResourceName(booking.getResourceId(), authToken);
                BookingResponse response = BookingResponse.fromBooking(booking, resourceName);

                if (userCheckedIn) {
                    eventPublisher.publishBookingCompleted(response);
                    logger.info("Published booking.completed event for resource {} (no other active bookings)",
                            booking.getResourceId());
                } else {
                    eventPublisher.publishBookingNoShow(response);
                    logger.info("Published booking.noshow event for resource {} (no other active bookings)",
                            booking.getResourceId());
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch resource name for booking {}: {}",
                        booking.getId(), e.getMessage());
                // Still publish event even if resource name fetch fails
                BookingResponse response = BookingResponse.fromBooking(booking, "Unknown Resource");
                if (userCheckedIn) {
                    eventPublisher.publishBookingCompleted(response);
                } else {
                    eventPublisher.publishBookingNoShow(response);
                }
            }
        } else {
            logger.debug("Not freeing resource {} - has other active bookings", booking.getResourceId());
        }
    }

    /**
     * Verify user is not restricted
     */
    private void verifyUserNotRestricted(Long userId, String authToken) {
        try {
            String url = userServiceUrl + "/api/users/" + userId + "/restricted";

            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
                logger.debug("Forwarding Authorization header to User Service (length={} chars)", authToken.length());
            } else {
                logger.warn("No Authorization header available when calling User Service for userId={}", userId);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

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
    private void verifyResourceAvailable(Long resourceId, String authToken) {
        try {
            if (catalogServiceUrl == null || catalogServiceUrl.isEmpty()) {
                logger.error("Catalog service URL is not configured");
                throw new ResourceUnavailableException("Catalog service is not available");
            }

            String url = catalogServiceUrl + "/api/resources/" + resourceId;
            logger.debug("Checking resource availability at: {}", url);

            if (url == null || url.isEmpty() || !url.startsWith("http")) {
                logger.error("Invalid URL constructed: {}", url);
                throw new ResourceUnavailableException("Invalid catalog service URL configuration");
            }

            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() != HttpStatus.OK) {
                logger.error("Resource check returned status: {} for resourceId: {}", response.getStatusCode(),
                        resourceId);
                throw new ResourceUnavailableException("Resource not found: " + resourceId);
            }

            logger.debug("Resource {} is available", resourceId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            logger.error("Resource not found: {} - {}", resourceId, e.getMessage());
            throw new ResourceUnavailableException("Resource not found: " + resourceId);
        } catch (RestClientException e) {
            logger.error("Failed to verify resource {}: {} - Error: {} - URL was: {}",
                    resourceId, e.getMessage(), e.getClass().getName(),
                    catalogServiceUrl + "/api/resources/" + resourceId, e);
            throw new ResourceUnavailableException("Failed to verify resource availability: " + e.getMessage());
        } catch (Exception e) {
            String attemptedUrl = catalogServiceUrl != null ? catalogServiceUrl + "/api/resources/" + resourceId
                    : "null";
            logger.error("Unexpected error verifying resource {}: {} - Attempted URL: {}",
                    resourceId, e.getMessage(), attemptedUrl, e);
            throw new ResourceUnavailableException("Failed to verify resource availability: " + e.getMessage());
        }
    }

    /**
     * Get resource name from catalog service
     */
    private String getResourceName(Long resourceId, String authToken) {
        try {
            String url = catalogServiceUrl + "/api/resources/" + resourceId;

            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object name = response.getBody().get("name");
                return name != null ? name.toString() : "Unknown Resource";
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch resource name for resourceId {}: {}", resourceId, e.getMessage());
        }
        return "Unknown Resource";
    }

    /**
     * Get user info by ID (for admin booking list)
     */
    private Map<String, String> getUserInfo(Long userId, String authToken) {
        Map<String, String> userInfo = new java.util.HashMap<>();
        userInfo.put("name", "User " + userId);
        userInfo.put("email", "");

        try {
            String url = userServiceUrl + "/api/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("name")) {
                    userInfo.put("name", (String) body.get("name"));
                }
                if (body.containsKey("email")) {
                    userInfo.put("email", (String) body.get("email"));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get user info for {}: {}", userId, e.getMessage());
        }
        return userInfo;
    }

    /**
     * Validate booking with Policy Service
     */
    private PolicyValidationResponse validateWithPolicyService(PolicyValidationRequest request, String authToken) {
        try {
            String url = policyServiceUrl + "/api/policies/validate";
            logger.debug("Validating booking with policy service at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
                logger.debug("Forwarding Authorization header to Policy Service (length={} chars)", authToken.length());
            } else {
                logger.warn("No Authorization header available when calling Policy Service");
            }
            HttpEntity<PolicyValidationRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PolicyValidationResponse> response = restTemplate.postForEntity(
                    url, entity, PolicyValidationResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Policy validation result: valid={}", response.getBody().isValid());
                return response.getBody();
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            logger.warn("Policy service unavailable (connection failed): {} - Continuing with validation passed",
                    e.getMessage());
            // Return valid if policy service is unavailable (fail open)
            PolicyValidationResponse validation = new PolicyValidationResponse();
            validation.setValid(true);
            return validation;
        } catch (RestClientException e) {
            logger.warn("Failed to validate with policy service: {} - Continuing with validation passed",
                    e.getMessage());
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
     * Get grace period from policy service
     * Grace period is the time window after booking start time during which
     * check-in is allowed
     * 
     * @param authToken Authentication token for policy service call
     * @return Grace period in minutes (defaults to 15 if policy service is
     *         unavailable)
     */
    private int getGracePeriodFromPolicy(String authToken) {
        try {
            String url = policyServiceUrl + "/api/policies?active=true";
            logger.debug("Fetching active policy from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", authToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null
                    && !response.getBody().isEmpty()) {
                Map<String, Object> activePolicy = response.getBody().get(0);
                Object gracePeriodObj = activePolicy.get("gracePeriodMinutes");
                if (gracePeriodObj != null) {
                    int gracePeriod;
                    if (gracePeriodObj instanceof Number) {
                        gracePeriod = ((Number) gracePeriodObj).intValue();
                    } else {
                        gracePeriod = Integer.parseInt(gracePeriodObj.toString());
                    }
                    logger.debug("Retrieved grace period from policy: {} minutes", gracePeriod);
                    return gracePeriod;
                }
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            logger.warn("Policy service unavailable (connection failed): {} - Using default grace period of 15 minutes",
                    e.getMessage());
        } catch (RestClientException e) {
            logger.warn(
                    "Failed to fetch grace period from policy service: {} - Using default grace period of 15 minutes",
                    e.getMessage());
        } catch (Exception e) {
            logger.warn(
                    "Unexpected error fetching grace period from policy service: {} - Using default grace period of 15 minutes",
                    e.getMessage());
        }

        // Default grace period if policy service is unavailable
        logger.debug("Using default grace period: 15 minutes");
        return 15;
    }

    /**
     * Generate unique QR code
     */
    private String generateQRCode() {
        return "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
