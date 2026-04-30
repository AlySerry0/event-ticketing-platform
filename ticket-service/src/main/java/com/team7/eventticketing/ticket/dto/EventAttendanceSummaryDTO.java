package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventAttendanceSummaryDTO {

    private Long eventId;
    private long totalTickets;
    private long usedTickets;
    private long validTickets;
    private double attendanceRate;
    private LocalDateTime lastCheckIn;

}