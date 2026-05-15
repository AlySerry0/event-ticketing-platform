package com.team7.eventticketing.user.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String id = MDC.get("correlationId");
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            template.header("X-Correlation-ID", id);
        };
    }
}