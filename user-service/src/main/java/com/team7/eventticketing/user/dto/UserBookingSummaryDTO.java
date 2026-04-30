package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.pattern.builder.BaseBuilder;

public class UserBookingSummaryDTO {

    private Long userId;
    private String name;
    private Long totalBookings;
    private Long completedBookings;
    private Long cancelledBookings;
    private Double totalSpent;
    private Double averageBookingAmount;

    public UserBookingSummaryDTO() {}

    private UserBookingSummaryDTO(Builder builder) {
        this.userId               = builder.userId;
        this.name                 = builder.name;
        this.totalBookings        = builder.totalBookings;
        this.completedBookings    = builder.completedBookings;
        this.cancelledBookings    = builder.cancelledBookings;
        this.totalSpent           = builder.totalSpent;
        this.averageBookingAmount = builder.averageBookingAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseBuilder<UserBookingSummaryDTO, Builder> {

        private Long userId;
        private String name;
        private Long totalBookings;
        private Long completedBookings;
        private Long cancelledBookings;
        private Double totalSpent;
        private Double averageBookingAmount;

        private Builder() {}

        public Builder userId(Long userId) {
            this.userId = userId;
            return self();
        }

        public Builder name(String name) {
            this.name = name;
            return self();
        }

        public Builder totalBookings(Long totalBookings) {
            this.totalBookings = totalBookings;
            return self();
        }

        public Builder completedBookings(Long completedBookings) {
            this.completedBookings = completedBookings;
            return self();
        }

        public Builder cancelledBookings(Long cancelledBookings) {
            this.cancelledBookings = cancelledBookings;
            return self();
        }

        public Builder totalSpent(Double totalSpent) {
            this.totalSpent = totalSpent;
            return self();
        }

        public Builder averageBookingAmount(Double averageBookingAmount) {
            this.averageBookingAmount = averageBookingAmount;
            return self();
        }

        @Override
        public UserBookingSummaryDTO build() {
            return new UserBookingSummaryDTO(this);
        }
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