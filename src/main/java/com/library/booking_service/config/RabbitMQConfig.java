package com.library.booking_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for event publishing
 */
@Configuration
public class RabbitMQConfig {
    
    // Exchange names
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String AUDIT_EXCHANGE = "audit.events";
    
    // Queue names
    public static final String BOOKING_CREATED_QUEUE = "booking.created";
    public static final String BOOKING_UPDATED_QUEUE = "booking.updated";
    public static final String BOOKING_CANCELED_QUEUE = "booking.canceled";
    public static final String BOOKING_CHECKED_IN_QUEUE = "booking.checked_in";
    public static final String BOOKING_NO_SHOW_QUEUE = "booking.no_show";
    
    // Routing keys
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.created";
    public static final String BOOKING_UPDATED_ROUTING_KEY = "booking.updated";
    public static final String BOOKING_CANCELED_ROUTING_KEY = "booking.canceled";
    public static final String BOOKING_CHECKED_IN_ROUTING_KEY = "booking.checked_in";
    public static final String BOOKING_NO_SHOW_ROUTING_KEY = "booking.no_show";
    
    /**
     * Create topic exchanges
     */
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }
    
    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE, true, false);
    }
    
    /**
     * Create queues for booking events
     */
    @Bean
    public Queue bookingCreatedQueue() {
        return new Queue(BOOKING_CREATED_QUEUE, true);
    }
    
    @Bean
    public Queue bookingUpdatedQueue() {
        return new Queue(BOOKING_UPDATED_QUEUE, true);
    }
    
    @Bean
    public Queue bookingCanceledQueue() {
        return new Queue(BOOKING_CANCELED_QUEUE, true);
    }
    
    @Bean
    public Queue bookingCheckedInQueue() {
        return new Queue(BOOKING_CHECKED_IN_QUEUE, true);
    }
    
    @Bean
    public Queue bookingNoShowQueue() {
        return new Queue(BOOKING_NO_SHOW_QUEUE, true);
    }
    
    /**
     * Bind queues to exchange
     */
    @Bean
    public Binding bookingCreatedBinding() {
        return BindingBuilder
            .bind(bookingCreatedQueue())
            .to(bookingExchange())
            .with(BOOKING_CREATED_ROUTING_KEY);
    }
    
    @Bean
    public Binding bookingUpdatedBinding() {
        return BindingBuilder
            .bind(bookingUpdatedQueue())
            .to(bookingExchange())
            .with(BOOKING_UPDATED_ROUTING_KEY);
    }
    
    @Bean
    public Binding bookingCanceledBinding() {
        return BindingBuilder
            .bind(bookingCanceledQueue())
            .to(bookingExchange())
            .with(BOOKING_CANCELED_ROUTING_KEY);
    }
    
    @Bean
    public Binding bookingCheckedInBinding() {
        return BindingBuilder
            .bind(bookingCheckedInQueue())
            .to(bookingExchange())
            .with(BOOKING_CHECKED_IN_ROUTING_KEY);
    }
    
    @Bean
    public Binding bookingNoShowBinding() {
        return BindingBuilder
            .bind(bookingNoShowQueue())
            .to(bookingExchange())
            .with(BOOKING_NO_SHOW_ROUTING_KEY);
    }
    
    /**
     * JSON message converter
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}





