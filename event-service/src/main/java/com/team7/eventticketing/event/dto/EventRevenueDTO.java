package com.team7.eventticketing.event.dto;

/**
 * DTO for Event Booking Revenue Summary (S2-F3).
 *
 * Builder Pattern retrofit (M2 requirement):
 * Has 5 fields → Builder required.
 * Usage:
 *   EventRevenueDTO dto = EventRevenueDTO.builder()
 *       .eventId(1L)
 *       .name("Cairo Jazz")
 *       .totalBookings(5L)
 *       .totalRevenue(3500.0)
 *       .averageBookingAmount(700.0)
 *       .build();
 */
public class EventRevenueDTO {

    private final Long eventId;
    private final String name;
    private final Long totalBookings;
    private final Double totalRevenue;
    private final Double averageBookingAmount;

    // Private constructor — only the Builder may call this
    private EventRevenueDTO(Builder builder) {
        this.eventId              = builder.eventId;
        this.name                 = builder.name;
        this.totalBookings        = builder.totalBookings;
        this.totalRevenue         = builder.totalRevenue;
        this.averageBookingAmount = builder.averageBookingAmount;
    }

    // Legacy all-args constructor kept for backward compatibility with existing call sites
    public EventRevenueDTO(Long eventId, String name, Long totalBookings,
                           Double totalRevenue, Double averageBookingAmount) {
        this.eventId              = eventId;
        this.name                 = name;
        this.totalBookings        = totalBookings;
        this.totalRevenue         = totalRevenue;
        this.averageBookingAmount = averageBookingAmount;
    }

    // -----------------------------------------------------------------------
    // Builder entry point
    // -----------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    // -----------------------------------------------------------------------
    // Static inner Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private Long eventId;
        private String name;
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageBookingAmount;

        private Builder() {}

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalBookings(Long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageBookingAmount(Double averageBookingAmount) {
            this.averageBookingAmount = averageBookingAmount;
            return this;
        }

        public EventRevenueDTO build() {
            return new EventRevenueDTO(this);
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public Long getEventId()              { return eventId; }
    public String getName()               { return name; }
    public Long getTotalBookings()        { return totalBookings; }
    public Double getTotalRevenue()       { return totalRevenue; }
    public Double getAverageBookingAmount() { return averageBookingAmount; }
}