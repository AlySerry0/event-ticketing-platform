package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.TicketAnalyticsDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TicketAnalyticsAdapter implements ObjectArrayAdapter<TicketAnalyticsDTO>{

    @Override
    public TicketAnalyticsDTO convert(Object[] row) {

        long total = ((Number) row[0]).longValue();
        long used = ((Number) row[1]).longValue();
        long valid = ((Number) row[2]).longValue();
        long expired = ((Number) row[3]).longValue();
        long cancelled = ((Number) row[4]).longValue();

        double rate = total == 0 ? 0.0 : (double) used / total;

        Map<String, Long> statusMap = (total == 0)
                ? Map.of()
                : Map.of(
                "USED", used,
                "VALID", valid,
                "EXPIRED", expired,
                "CANCELLED", cancelled
        );

        return TicketAnalyticsDTO.builder()
                .totalIssued(total)
                .usedCount(used)
                .validCount(valid)
                .expiredCount(expired)
                .cancelledCount(cancelled)
                .attendanceRate(rate)
                .ticketsByStatus(statusMap)
                .build();
    }
}