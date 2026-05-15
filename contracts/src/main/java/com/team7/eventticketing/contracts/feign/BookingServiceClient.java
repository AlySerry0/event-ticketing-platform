package com.team7.eventticketing.contracts.feign;

import com.team7.eventticketing.contracts.dto.BookingDTO;
import com.team7.eventticketing.contracts.dto.BookingItemDTO;
import com.team7.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team7.eventticketing.contracts.dto.EventBookingRevenueDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url:http://booking-service:8080}")
public interface BookingServiceClient {

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/count")
    long getTotalBookingCount(@PathVariable("userId") Long userId,
                              @RequestParam(required = false) String status);

    @GetMapping("/api/bookings/user/{userId}/total")
    BigDecimal getUserBookingTotal(@PathVariable Long userId,
                                   @RequestParam String startDate,
                                   @RequestParam String endDate);

    @GetMapping("/api/bookings/event/{eventId}/revenue")
    EventBookingRevenueDTO getEventRevenue(@PathVariable Long eventId,
                                           @RequestParam String startDate,
                                           @RequestParam String endDate);

    @GetMapping("/api/bookings/event/{eventId}/active-count")
    int getEventActiveBookingCount(@PathVariable Long eventId);

    @GetMapping("/api/bookings/{bookingId}")
    BookingDTO getBooking(@PathVariable Long bookingId);

    @GetMapping("/api/bookings/{bookingId}/items")
    List<BookingItemDTO> getBookingItems(@PathVariable Long bookingId);
}
