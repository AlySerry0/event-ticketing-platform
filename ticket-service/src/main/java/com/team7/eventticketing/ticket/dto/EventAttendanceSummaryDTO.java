package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class EventAttendanceSummaryDTO {

    private Long eventId;
    private long totalTickets;
    private long usedTickets;
    private long validTickets;
    private double attendanceRate;
    private LocalDateTime lastCheckIn;

    public EventAttendanceSummaryDTO() {}

    public EventAttendanceSummaryDTO(Long eventId, long totalTickets, long usedTickets,
                                     long validTickets, double attendanceRate,
                                     LocalDateTime lastCheckIn) {
        this.eventId = eventId;
        this.totalTickets = totalTickets;
        this.usedTickets = usedTickets;
        this.validTickets = validTickets;
        this.attendanceRate = attendanceRate;
        this.lastCheckIn = lastCheckIn;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public long getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(long totalTickets) {
        this.totalTickets = totalTickets;
    }

    public long getUsedTickets() {
        return usedTickets;
    }

    public void setUsedTickets(long usedTickets) {
        this.usedTickets = usedTickets;
    }

    public long getValidTickets() {
        return validTickets;
    }

    public void setValidTickets(long validTickets) {
        this.validTickets = validTickets;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }

    public LocalDateTime getLastCheckIn() {
        return lastCheckIn;
    }

    public void setLastCheckIn(LocalDateTime lastCheckIn) {
        this.lastCheckIn = lastCheckIn;
    }
}