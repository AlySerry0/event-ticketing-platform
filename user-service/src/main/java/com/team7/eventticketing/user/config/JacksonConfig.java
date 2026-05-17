package com.team7.eventticketing.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Provides a Jackson 2 ObjectMapper bean for our consumers.
     * Spring Boot 4 defaults to Jackson 3 (tools.jackson.*) for auto-config, so the
     * legacy com.fasterxml.jackson.databind.ObjectMapper that we inject into
     * BookingEventConsumer is not registered automatically.
     *
     * JavaTimeModule is required because slice 8's BookingCompletedEvent /
     * BookingCancelledEvent records carry a LocalDateTime field (occurredAt)
     * which Jackson can't deserialize out of the box.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}