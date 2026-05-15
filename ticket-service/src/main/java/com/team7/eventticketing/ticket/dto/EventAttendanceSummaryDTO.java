package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class EventAttendanceSummaryDTO {

    private Long eventId;
    private long totalTickets;
    private long usedTickets;
    private long validTickets;
    private double attendanceRate;
    private LocalDateTime lastCheckIn;

    private EventAttendanceSummaryDTO() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EventAttendanceSummaryDTO dto = new EventAttendanceSummaryDTO();

        public Builder eventId(Long eventId) {
            dto.eventId = eventId;
            return this;
        }

        public Builder totalTickets(long totalTickets) {
            dto.totalTickets = totalTickets;
            return this;
        }

        public Builder usedTickets(long usedTickets) {
            dto.usedTickets = usedTickets;
            return this;
        }

        public Builder validTickets(long validTickets) {
            dto.validTickets = validTickets;
            return this;
        }

        public Builder attendanceRate(double attendanceRate) {
            dto.attendanceRate = attendanceRate;
            return this;
        }

        public Builder lastCheckIn(LocalDateTime lastCheckIn) {
            dto.lastCheckIn = lastCheckIn;
            return this;
        }

        public EventAttendanceSummaryDTO build() {
            return dto;
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public long getTotalTickets() {
        return totalTickets;
    }

    public long getUsedTickets() {
        return usedTickets;
    }

    public long getValidTickets() {
        return validTickets;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public LocalDateTime getLastCheckIn() {
        return lastCheckIn;
    }

    // Alias getters for EventTicketSummaryDTO Feign client deserialization compatibility
    public long getTotalTicketsSold() {
        return totalTickets;
    }

    public int getUsedCount() {
        return (int) usedTickets;
    }
}