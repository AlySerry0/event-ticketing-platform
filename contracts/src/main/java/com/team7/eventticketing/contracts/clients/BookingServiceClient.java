package com.team7.eventticketing.contracts.clients;

import com.team7.eventticketing.contracts.dto.BookingSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url:http://booking-service:8080}")
public interface BookingServiceClient {
    /**
     * Returns aggregated booking stats for a user.
     * Used by user-service to enrich user profile responses.
     */
    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(
            @PathVariable("userId") Long userId,
            @RequestHeader("Authorization") String token,
            @RequestHeader(value = "X-Correlation-ID", required = false)
            String correlationId);

//    /**
//     * Returns count of active (PENDING or CONFIRMED) bookings for a user.
//     * Used by S1-F4 deactivate-user to block deactivation.
//     */
//    @GetMapping("/api/bookings/user/{userId}/active-count")
//    int getActiveBookingCount(
//            @PathVariable("userId") Long userId,
//            @RequestHeader("Authorization") String token,
//            @RequestHeader(value = "X-Correlation-ID", required = false)
//            String correlationId);
//
//    /**
//     * Returns total lifetime booking count for a user.
//     */
//    @GetMapping("/api/bookings/user/{userId}/count")
//    long getTotalBookingCount(
//            @PathVariable("userId") Long userId,
//            @RequestHeader("Authorization") String token,
//            @RequestHeader(value = "X-Correlation-ID", required = false)
//            String correlationId);
}