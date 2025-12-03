package com.library.booking_service.service;

import com.library.booking_service.config.RabbitMQConfig;
import com.library.booking_service.dto.BookingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for publishing booking events to RabbitMQ
 */
@Service
public class BookingEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingEventPublisher.class);
    
    private final RabbitTemplate rabbitTemplate;
    
    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publishBookingCreated(BookingResponse booking) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CREATED_ROUTING_KEY,
                booking
            );
            logger.info("Published booking.created event for booking: {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to publish booking.created event: {}", e.getMessage());
        }
    }
    
    public void publishBookingUpdated(BookingResponse booking) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_UPDATED_ROUTING_KEY,
                booking
            );
            logger.info("Published booking.updated event for booking: {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to publish booking.updated event: {}", e.getMessage());
        }
    }
    
    public void publishBookingCanceled(BookingResponse booking) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CANCELED_ROUTING_KEY,
                booking
            );
            logger.info("Published booking.canceled event for booking: {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to publish booking.canceled event: {}", e.getMessage());
        }
    }
    
    public void publishBookingCheckedIn(BookingResponse booking) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CHECKED_IN_ROUTING_KEY,
                booking
            );
            logger.info("Published booking.checked_in event for booking: {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to publish booking.checked_in event: {}", e.getMessage());
        }
    }
    
    public void publishBookingNoShow(BookingResponse booking) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_NO_SHOW_ROUTING_KEY,
                booking
            );
            logger.info("Published booking.no_show event for booking: {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to publish booking.no_show event: {}", e.getMessage());
        }
    }
}




