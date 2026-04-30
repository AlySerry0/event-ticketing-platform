package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.pattern.builder.BaseBuilder;

public class TopAttendeeDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long bookingCount;

    public TopAttendeeDTO() {}

    private TopAttendeeDTO(Builder builder) {
        this.userId       = builder.userId;
        this.name         = builder.name;
        this.totalSpent   = builder.totalSpent;
        this.bookingCount = builder.bookingCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseBuilder<TopAttendeeDTO, Builder> {

        private Long userId;
        private String name;
        private Double totalSpent;
        private Long bookingCount;

        private Builder() {}

        public Builder userId(Long userId) {
            this.userId = userId;
            return self();
        }

        public Builder name(String name) {
            this.name = name;
            return self();
        }

        public Builder totalSpent(Double totalSpent) {
            this.totalSpent = totalSpent;
            return self();
        }

        public Builder bookingCount(Long bookingCount) {
            this.bookingCount = bookingCount;
            return self();
        }

        @Override
        public TopAttendeeDTO build() {
            return new TopAttendeeDTO(this);
        }
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public Long getBookingCount() { return bookingCount; }
    public void setBookingCount(Long bookingCount) { this.bookingCount = bookingCount; }
}