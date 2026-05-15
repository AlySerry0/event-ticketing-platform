package com.team7.eventticketing.contracts.dto;

import java.math.BigDecimal;

public record BookingSummaryDTO(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        BigDecimal totalSpent,
        BigDecimal averageBookingAmount
) {
    public static BookingSummaryDTO empty() {
        return new BookingSummaryDTO(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
