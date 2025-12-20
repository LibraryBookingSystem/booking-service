package com.library.booking_service.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Jackson configuration for proper UTC timezone handling
 * Ensures LocalDateTime is parsed from UTC strings (ending with 'Z') correctly
 * and serialized with 'Z' suffix to indicate UTC
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        
        // Custom deserializer that handles UTC times (with 'Z' suffix)
        LocalDateTimeDeserializer deserializer = new LocalDateTimeDeserializer(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
            @Override
            public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p, 
                    com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
                String dateTimeString = p.getText().trim();
                
                // If string ends with 'Z' or has timezone info, parse as UTC Instant
                if (dateTimeString.endsWith("Z") || dateTimeString.contains("+") || 
                    (dateTimeString.contains("-") && dateTimeString.length() > 19)) {
                    try {
                        java.time.Instant instant = java.time.Instant.parse(dateTimeString);
                        LocalDateTime result = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                        org.slf4j.LoggerFactory.getLogger(JacksonConfig.class)
                            .debug("Parsed UTC time: {} -> {}", dateTimeString, result);
                        return result;
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(JacksonConfig.class)
                            .warn("Failed to parse UTC time: {}, falling back to default parser", dateTimeString);
                        // Fall back to default parsing if Instant.parse fails
                        return super.deserialize(p, ctxt);
                    }
                }
                
                // Otherwise, parse normally (for backward compatibility)
                org.slf4j.LoggerFactory.getLogger(JacksonConfig.class)
                    .debug("Parsing time without timezone: {}", dateTimeString);
                return super.deserialize(p, ctxt);
            }
        };
        
        // Custom serializer that adds 'Z' suffix to indicate UTC
        LocalDateTimeSerializer serializer = new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator g, SerializerProvider provider) throws IOException {
                // Serialize as ISO format with 'Z' suffix to indicate UTC
                String formatted = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
                g.writeString(formatted);
            }
        };
        
        javaTimeModule.addDeserializer(LocalDateTime.class, deserializer);
        javaTimeModule.addSerializer(LocalDateTime.class, serializer);
        
        return builder
                .modules(javaTimeModule)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToDisable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
    }
}


