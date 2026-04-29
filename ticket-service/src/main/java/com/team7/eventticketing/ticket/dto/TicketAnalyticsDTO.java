package com.team7.eventticketing.ticket.dto;

import java.util.Map;

public class TicketAnalyticsDTO {

    private final long totalIssued;
    private final long usedCount;
    private final long validCount;
    private final long expiredCount;
    private final long cancelledCount;
    private final double attendanceRate;
    private final Map<String, Long> ticketsByStatus;

    private TicketAnalyticsDTO(Builder builder) {
        this.totalIssued = builder.totalIssued;
        this.usedCount = builder.usedCount;
        this.validCount = builder.validCount;
        this.expiredCount = builder.expiredCount;
        this.cancelledCount = builder.cancelledCount;
        this.attendanceRate = builder.attendanceRate;
        this.ticketsByStatus = builder.ticketsByStatus;
    }

    public static class Builder {
        private long totalIssued;
        private long usedCount;
        private long validCount;
        private long expiredCount;
        private long cancelledCount;
        private double attendanceRate;
        private Map<String, Long> ticketsByStatus;

        public Builder totalIssued(long v) { this.totalIssued = v; return this; }
        public Builder usedCount(long v) { this.usedCount = v; return this; }
        public Builder validCount(long v) { this.validCount = v; return this; }
        public Builder expiredCount(long v) { this.expiredCount = v; return this; }
        public Builder cancelledCount(long v) { this.cancelledCount = v; return this; }
        public Builder attendanceRate(double v) { this.attendanceRate = v; return this; }
        public Builder ticketsByStatus(Map<String, Long> v) { this.ticketsByStatus = v; return this; }

        public TicketAnalyticsDTO build() {
            return new TicketAnalyticsDTO(this);
        }
    }

    public long getTotalIssued() { return totalIssued; }
    public long getUsedCount() { return usedCount; }
    public long getValidCount() { return validCount; }
    public long getExpiredCount() { return expiredCount; }
    public long getCancelledCount() { return cancelledCount; }
    public double getAttendanceRate() { return attendanceRate; }
    public Map<String, Long> getTicketsByStatus() { return ticketsByStatus; }

}