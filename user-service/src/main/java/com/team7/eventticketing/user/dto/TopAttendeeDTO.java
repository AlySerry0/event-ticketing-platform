package com.team7.eventticketing.user.dto;

public class TopAttendeeDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long bookingCount;

    public TopAttendeeDTO() {}

    public TopAttendeeDTO(Long userId, String name, Double totalSpent, Long bookingCount) {
        this.userId = userId;
        this.name = name;
        this.totalSpent = totalSpent;
        this.bookingCount = bookingCount;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }

    public Long getBookingCount() { return bookingCount; }
    public void setBookingCount(Long bookingCount) { this.bookingCount = bookingCount; }
}