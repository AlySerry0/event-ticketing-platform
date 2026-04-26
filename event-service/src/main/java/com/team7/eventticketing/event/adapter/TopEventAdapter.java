package com.team7.eventticketing.event.adapter;

import com.team7.eventticketing.event.dto.TopEventDTO;

/**
 * Adapter Pattern — converts a raw Object[] row returned by the native SQL query
 * {@code EventRepository.findTopRatedEvents} into a {@link TopEventDTO}.
 *
 * Used by S2-F6 (Top Rated Events Report).
 *
 * Expected row layout (matches the SELECT in EventRepository):
 *   row[0] — e.id             → eventId       (Number → Long)
 *   row[1] — e.name           → name          (String)
 *   row[2] — e.rating         → rating        (Number → Double, nullable)
 *   row[3] — COUNT(b.id)      → totalBookings (Number → Long)
 */
public class TopEventAdapter {

    /**
     * Converts a raw SQL result row to TopEventDTO.
     *
     * @param row the Object[] row from the native query (length >= 4)
     * @return populated TopEventDTO
     */
    public TopEventDTO adapt(Object[] row) {
        Long eventId       = ((Number) row[0]).longValue();
        String name        = (String) row[1];
        Double rating      = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        Long totalBookings = ((Number) row[3]).longValue();

        return TopEventDTO.builder()
                .eventId(eventId)
                .name(name)
                .rating(rating)
                .totalBookings(totalBookings)
                .build();
    }
}