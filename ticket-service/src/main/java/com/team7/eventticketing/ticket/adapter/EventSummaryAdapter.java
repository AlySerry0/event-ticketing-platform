package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EventSummaryAdapter implements ObjectArrayAdapter<EventAttendanceSummaryDTO> {

    @Override
    public EventAttendanceSummaryDTO convert(Object[] row) {

        long total = row[0] != null ? ((Number) row[0]).longValue() : 0;
        long used = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long valid = row[2] != null ? ((Number) row[2]).longValue() : 0;

        double attendanceRate = total == 0 ? 0 : (used * 100.0) / total;

        LocalDateTime lastCheckIn = null;
        if (row[3] != null) {
            if (row[3] instanceof java.sql.Timestamp ts) {
                lastCheckIn = ts.toLocalDateTime();
            } else if (row[3] instanceof LocalDateTime ldt) {
                lastCheckIn = ldt;
            }
        }

        return new EventAttendanceSummaryDTO(
                null, // eventId handled in service
                total,
                used,
                valid,
                attendanceRate,
                lastCheckIn
        );
    }
}