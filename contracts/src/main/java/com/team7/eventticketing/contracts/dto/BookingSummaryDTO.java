package com.team7.eventticketing.contracts.dto;

/**
 * Response shape from booking-service GET /api/bookings/user/{userId}/summary.
 * Defined locally so user-service compiles independently of booking-service.
 */
public class BookingSummaryDTO {

    private Long   userId;
    private Long   totalBookings;
    private Long   completedBookings;
    private Long   cancelledBookings;
    private Double totalSpent;
    private Double averageBookingAmount;

    // ── static factory for the empty/fallback case ─────────────────────
    public static BookingSummaryDTO empty() {
        BookingSummaryDTO dto = new BookingSummaryDTO();
        dto.userId            = null;
        dto.totalBookings     = 0L;
        dto.completedBookings = 0L;
        dto.cancelledBookings = 0L;
        dto.totalSpent        = 0.0;
        dto.averageBookingAmount = 0.0;
        return dto;
    }

    // ── getters / setters ───────────────────────────────────────────────
    public Long   getUserId()            { return userId; }
    public void   setUserId(Long v)      { this.userId = v; }
    public Long   getTotalBookings()     { return totalBookings; }
    public void   setTotalBookings(Long v) { this.totalBookings = v; }
    public Long   getCompletedBookings() { return completedBookings; }
    public void   setCompletedBookings(Long v) { this.completedBookings = v; }
    public Long   getCancelledBookings() { return cancelledBookings; }
    public void   setCancelledBookings(Long v) { this.cancelledBookings = v; }
    public Double getTotalSpent()        { return totalSpent; }
    public void   setTotalSpent(Double v){ this.totalSpent = v; }
    public Double getAverageBookingAmount() { return averageBookingAmount; }
    public void   setAverageBookingAmount(Double v) { this.averageBookingAmount = v; }
}