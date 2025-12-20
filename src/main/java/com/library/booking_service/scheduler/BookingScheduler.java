package com.library.booking_service.scheduler;

import com.library.booking_service.entity.Booking;
import com.library.booking_service.entity.BookingStatus;
import com.library.booking_service.repository.BookingRepository;
import com.library.booking_service.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Event-driven scheduler for processing booking-related tasks
 * Schedules individual tasks per booking instead of polling
 */
@Component
public class BookingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BookingScheduler.class);

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final TaskScheduler taskScheduler;
    
    // Track scheduled tasks so we can cancel them if booking is canceled/updated
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Value("${app.booking.grace-period-minutes:15}")
    private int gracePeriodMinutes;

    @Autowired
    public BookingScheduler(@Lazy BookingService bookingService, 
                           BookingRepository bookingRepository,
                           TaskScheduler taskScheduler) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.taskScheduler = taskScheduler;
    }

    /**
     * On startup, schedule completion tasks for all existing active bookings
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleExistingBookings() {
        logger.info("Scheduling completion tasks for existing active bookings");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Booking> activeBookings = bookingRepository.findAll().stream()
                .filter(b -> (b.getStatus() == BookingStatus.CONFIRMED || 
                             b.getStatus() == BookingStatus.CHECKED_IN) &&
                             b.getEndTime().isAfter(now))
                .toList();
        
        for (Booking booking : activeBookings) {
            scheduleBookingCompletion(booking.getId(), booking.getEndTime());
        }
        
        logger.info("Scheduled {} active bookings for completion", activeBookings.size());
    }

    /**
     * Schedule a task to complete a booking when its endTime arrives
     * This is event-driven - triggered when booking is created
     */
    public void scheduleBookingCompletion(Long bookingId, LocalDateTime endTime) {
        // Cancel existing task if any (in case booking was updated)
        cancelScheduledTask(bookingId);
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (endTime.isBefore(now) || endTime.isEqual(now)) {
            // Booking already expired, process immediately
            logger.debug("Booking {} already expired, processing immediately", bookingId);
            processBookingCompletion(bookingId);
            return;
        }

        // Calculate delay until endTime
        Duration delay = Duration.between(now, endTime);
        Instant scheduledTime = Instant.now().plus(delay);
        
        logger.info("Scheduling booking {} completion for {}", bookingId, scheduledTime);
        
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> processBookingCompletion(bookingId),
            scheduledTime
        );
        
        scheduledTasks.put(bookingId, future);
    }

    /**
     * Cancel scheduled completion task (e.g., if booking is canceled or updated)
     */
    public void cancelScheduledTask(Long bookingId) {
        ScheduledFuture<?> future = scheduledTasks.remove(bookingId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            logger.debug("Cancelled scheduled completion task for booking {}", bookingId);
        }
    }

    /**
     * Process completion for a specific booking
     */
    private void processBookingCompletion(Long bookingId) {
        try {
            logger.info("Processing completion for booking {}", bookingId);
            bookingService.completeBooking(bookingId, null);
            scheduledTasks.remove(bookingId);
        } catch (Exception e) {
            logger.error("Error processing booking completion for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }

    /**
     * Process no-show bookings every 5 minutes
     * This still needs polling since we can't predict when grace period expires
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void processNoShows() {
        try {
            bookingService.processNoShows(gracePeriodMinutes, null);
        } catch (Exception e) {
            logger.error("Error in scheduled task processNoShows: {}", e.getMessage(), e);
        }
    }
}
