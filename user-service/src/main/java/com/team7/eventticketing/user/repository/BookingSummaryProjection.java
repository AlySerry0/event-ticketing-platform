package com.team7.eventticketing.user.repository;

public interface BookingSummaryProjection {
    Long getUserId();
    String getName();
    Long getTotalBookings();
    Long getCompletedBookings();
    Long getCancelledBookings();
    Double getTotalSpent();
    Double getAverageBookingAmount();
}