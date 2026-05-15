package com.team7.eventticketing.contracts.dto;

public class UserBookingSummaryDTO {

    private Long userId;
    private String name;
    private Long totalBookings;
    private Long completedBookings;
    private Long cancelledBookings;
    private Double totalSpent;
    private Double averageBookingAmount;

    public UserBookingSummaryDTO() {}

    private UserBookingSummaryDTO(Long userId, String name, Long totalBookings, Long completedBookings, Long cancelledBookings, Double totalSpent, Double averageBookingAmount) {
        this.userId               = userId;
        this.name                 = name;
        this.totalBookings        = totalBookings;
        this.completedBookings    = completedBookings;
        this.cancelledBookings    = cancelledBookings;
        this.totalSpent           = totalSpent;
        this.averageBookingAmount = averageBookingAmount;
    }

    // Getters and Setters (kept for Jackson serialization)
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }
    public Long getCompletedBookings() { return completedBookings; }
    public void setCompletedBookings(Long completedBookings) { this.completedBookings = completedBookings; }
    public Long getCancelledBookings() { return cancelledBookings; }
    public void setCancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public Double getAverageBookingAmount() { return averageBookingAmount; }
    public void setAverageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; }
}