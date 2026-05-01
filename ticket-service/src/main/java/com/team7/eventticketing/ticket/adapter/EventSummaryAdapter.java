package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EventSummaryAdapter implements ObjectArrayAdapter<EventAttendanceSummaryDTO> {

    @Override
    public EventAttendanceSummaryDTO convert(Object[] row) {

        Long eventId = row[0] != null ? ((Number) row[0]).longValue() : null;
        long total = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long used = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long valid = row[3] != null ? ((Number) row[3]).longValue() : 0;

        double attendanceRate = total == 0 ? 0 : (used * 100.0) / total;

        LocalDateTime lastCheckIn = null;
        if (row[4] instanceof java.sql.Timestamp ts) {
            lastCheckIn = ts.toLocalDateTime();
        } else if (row[4] instanceof LocalDateTime ldt) {
            lastCheckIn = ldt;
        }

        return EventAttendanceSummaryDTO.builder()
                .eventId(eventId)
                .totalTickets(total)
                .usedTickets(used)
                .validTickets(valid)
                .attendanceRate(attendanceRate)
                .lastCheckIn(lastCheckIn)
                .build();
    }
}