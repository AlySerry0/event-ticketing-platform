package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.TicketAnalyticsDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TicketAnalyticsAdapter implements ObjectArrayAdapter<TicketAnalyticsDTO>{

    @Override
    public TicketAnalyticsDTO convert(Object[] row) {
        // Note: PostgreSQL native queries often return BigInteger for counts
        long total = (row[0] != null) ? ((Number) row[0]).longValue() : 0L;
        long used = (row[1] != null) ? ((Number) row[1]).longValue() : 0L;
        long valid = (row[2] != null) ? ((Number) row[2]).longValue() : 0L;
        long expired = (row[3] != null) ? ((Number) row[3]).longValue() : 0L;
        long cancelled = (row[4] != null) ? ((Number) row[4]).longValue() : 0L;

        double rate = (total == 0) ? 0.0 : (double) used / total;

        Map<String, Long> statusMap = (total == 0) ? Map.of() : Map.of(
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