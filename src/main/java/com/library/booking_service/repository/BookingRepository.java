package com.library.booking_service.repository;

import com.library.booking_service.entity.Booking;
import com.library.booking_service.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Booking entity
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    /**
     * Find bookings by user ID
     */
    List<Booking> findByUserId(Long userId);
    
    /**
     * Find bookings by resource ID
     */
    List<Booking> findByResourceId(Long resourceId);
    
    /**
     * Find bookings by status
     */
    List<Booking> findByStatus(BookingStatus status);
    
    /**
     * Find booking by QR code
     */
    Optional<Booking> findByQrCode(String qrCode);
    
    /**
     * Find active bookings for a user (CONFIRMED or CHECKED_IN)
     */
    @Query("SELECT b FROM Booking b WHERE b.userId = :userId AND " +
           "(b.status = 'CONFIRMED' OR b.status = 'CHECKED_IN')")
    List<Booking> findActiveBookingsByUserId(@Param("userId") Long userId);
    
    /**
     * Find bookings that overlap with given time range for a resource
     */
    @Query("SELECT b FROM Booking b WHERE b.resourceId = :resourceId AND " +
           "b.status IN ('CONFIRMED', 'CHECKED_IN', 'PENDING') AND " +
           "((b.startTime <= :startTime AND b.endTime > :startTime) OR " +
           "(b.startTime < :endTime AND b.endTime >= :endTime) OR " +
           "(b.startTime >= :startTime AND b.endTime <= :endTime))")
    List<Booking> findOverlappingBookings(@Param("resourceId") Long resourceId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find confirmed bookings that haven't been checked in and are past grace period
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' AND " +
           "b.startTime <= :currentTime AND " +
           "b.startTime >= :gracePeriodStart")
    List<Booking> findNoShowBookings(@Param("currentTime") LocalDateTime currentTime,
                                    @Param("gracePeriodStart") LocalDateTime gracePeriodStart);
}




