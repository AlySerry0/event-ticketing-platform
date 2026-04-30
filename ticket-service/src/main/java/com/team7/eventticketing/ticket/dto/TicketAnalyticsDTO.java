package com.team7.eventticketing.ticket.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TicketAnalyticsDTO {

    private final long totalIssued;
    private final long usedCount;
    private final long validCount;
    private final long expiredCount;
    private final long cancelledCount;
    private final double attendanceRate;
    private final Map<String, Long> ticketsByStatus;

}