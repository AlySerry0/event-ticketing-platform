package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.TicketAnalyticsDTO;

import java.util.Map;

public class TicketAnalyticsAdapter implements ObjectArrayAdapter<TicketAnalyticsDTO>{

    public TicketAnalyticsDTO convert(Object[] row) {

        long total = ((Number) row[0]).longValue();
        long used = ((Number) row[1]).longValue();
        long valid = ((Number) row[2]).longValue();
        long expired = ((Number) row[3]).longValue();
        long cancelled = ((Number) row[4]).longValue();

        double rate = total == 0 ? 0.0 : (double) used / total;

        return new TicketAnalyticsDTO.Builder()
                .totalIssued(total)
                .usedCount(used)
                .validCount(valid)
                .expiredCount(expired)
                .cancelledCount(cancelled)
                .attendanceRate(rate)
                .ticketsByStatus(Map.of(
                        "USED", used,
                        "VALID", valid,
                        "EXPIRED", expired,
                        "CANCELLED", cancelled
                ))
                .build();
    }
}