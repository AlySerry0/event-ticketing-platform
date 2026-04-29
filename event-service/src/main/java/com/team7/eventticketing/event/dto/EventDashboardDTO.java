package com.team7.eventticketing.event.dto;

/**
 * DTO for Event Performance Dashboard (S2-F12).
 *
 * Builder Pattern retrofit (M2 requirement):
 * Has 7 fields, so it uses a static inner Builder for readable construction.
 */
public class EventDashboardDTO {

    private final Long eventId;
    private final String name;
    private final Long totalBookings;
    private final Long totalTicketsSold;
    private final Double totalRevenue;
    private final Double averageAttendanceRate;
    private final Double averageRating;

    private EventDashboardDTO(Builder builder) {
        this.eventId = builder.eventId;
        this.name = builder.name;
        this.totalBookings = builder.totalBookings;
        this.totalTicketsSold = builder.totalTicketsSold;
        this.totalRevenue = builder.totalRevenue;
        this.averageAttendanceRate = builder.averageAttendanceRate;
        this.averageRating = builder.averageRating;
    }

    // Legacy all-args constructor kept for compatibility.
    public EventDashboardDTO(Long eventId,
                             String name,
                             Long totalBookings,
                             Long totalTicketsSold,
                             Double totalRevenue,
                             Double averageAttendanceRate,
                             Double averageRating) {
        this.eventId = eventId;
        this.name = name;
        this.totalBookings = totalBookings;
        this.totalTicketsSold = totalTicketsSold;
        this.totalRevenue = totalRevenue;
        this.averageAttendanceRate = averageAttendanceRate;
        this.averageRating = averageRating;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long eventId;
        private String name;
        private Long totalBookings;
        private Long totalTicketsSold;
        private Double totalRevenue;
        private Double averageAttendanceRate;
        private Double averageRating;

        private Builder() {
        }

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

        public Builder totalTicketsSold(Long totalTicketsSold) {
            this.totalTicketsSold = totalTicketsSold;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageAttendanceRate(Double averageAttendanceRate) {
            this.averageAttendanceRate = averageAttendanceRate;
            return this;
        }

        public Builder averageRating(Double averageRating) {
            this.averageRating = averageRating;
            return this;
        }

        public EventDashboardDTO build() {
            return new EventDashboardDTO(this);
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public Long getTotalBookings() {
        return totalBookings;
    }

    public Long getTotalTicketsSold() {
        return totalTicketsSold;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Double getAverageAttendanceRate() {
        return averageAttendanceRate;
    }

    public Double getAverageRating() {
        return averageRating;
    }
}
