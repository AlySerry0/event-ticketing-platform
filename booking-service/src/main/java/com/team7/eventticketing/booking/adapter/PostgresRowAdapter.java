package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.dto.EventRecommendationDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PostgresRowAdapter {

    public EventRecommendationDTO adapt(Object[] row, Long score) {
        return new EventRecommendationDTO.Builder()
                .eventId(((Number) row[0]).longValue())
                .name((String) row[1])
                .category((String) row[2])
                .eventDate(toLocalDateTime(row[3]))
                .score(score)
                .build();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }

        return null;
    }
}