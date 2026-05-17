package com.team7.eventticketing.user.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String id = MDC.get("correlationId");
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            template.header("X-Correlation-ID", id);

            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String auth = request.getHeader("Authorization");
                if (auth != null) template.header("Authorization", auth);
                String userId = request.getHeader("X-User-Id");
                if (userId != null) template.header("X-User-Id", userId);
                String role = request.getHeader("X-User-Role");
                if (role != null) template.header("X-User-Role", role);
            }
        };
    }
}