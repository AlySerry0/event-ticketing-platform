package com.team7.eventticketing.event.dto;

public class EventRevenueDTO {

    private Long eventId;
    private String name;
    private Long totalBookings;
    private Double totalRevenue;
    private Double averageBookingAmount;

    public EventRevenueDTO(Long eventId, String name, Long totalBookings, Double totalRevenue, Double averageBookingAmount) {
        this.eventId = eventId;
        this.name = name;
        this.totalBookings = totalBookings;
        this.totalRevenue = totalRevenue;
        this.averageBookingAmount = averageBookingAmount;
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

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Double getAverageBookingAmount() {
        return averageBookingAmount;
    }
}
