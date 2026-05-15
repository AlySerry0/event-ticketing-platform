package com.team7.eventticketing.contracts.dto;

public class EventBookingRevenueDTO {

    private Long totalBookings;
    private Double totalRevenue;
    private Double averageBookingAmount;

    public EventBookingRevenueDTO(){
    }

    public EventBookingRevenueDTO(Long totalBookings, Double totalRevenue, Double averageBookingAmount) {
        this.totalBookings = totalBookings;
        this.totalRevenue = totalRevenue;
        this.averageBookingAmount = averageBookingAmount;
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

    public void setTotalBookings(Long totalBookings) {
        this.totalBookings = totalBookings;
    }

    public void setTotalRevenue(Double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public void setAverageBookingAmount(Double averageBookingAmount) {
        this.averageBookingAmount = averageBookingAmount;
    }
}
