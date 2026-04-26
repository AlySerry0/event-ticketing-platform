package com.team7.eventticketing.event.adapter;

import com.team7.eventticketing.event.dto.EventRevenueDTO;

/**
 * Adapter Pattern — converts a raw Object[] row returned by the native SQL query
 * {@code EventRepository.findEventRevenueSummary} into an {@link EventRevenueDTO}.
 *
 * Used by S2-F3 (Get Event Booking Revenue Summary).
 *
 * Before this adapter the mapping was done inline in EventService with direct
 * casting. Moving it here satisfies the M2 Adapter retrofit requirement for
 * features that use Object[] native SQL projections.
 *
 * Expected row layout (matches the SELECT in EventRepository):
 *   row[0] — COUNT(*)            → totalBookings  (Number → Long)
 *   row[1] — COALESCE(SUM(...))  → totalRevenue   (Number → Double)
 *   row[2] — COALESCE(AVG(...))  → averageBookingAmount (Number → Double)
 */
public class EventRevenueAdapter {

    /**
     * Converts a raw SQL result row to EventRevenueDTO.
     *
     * @param row       the Object[] row from the native query (length >= 3)
     * @param eventId   the event's PG id (already known at call site)
     * @param eventName the event's name (already known at call site)
     * @return populated EventRevenueDTO
     */
    public EventRevenueDTO adapt(Object[] row, Long eventId, String eventName) {
        Long totalBookings         = row[0] != null ? ((Number) row[0]).longValue()   : 0L;
        Double totalRevenue        = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        Double averageBookingAmount = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;

        return EventRevenueDTO.builder()
                .eventId(eventId)
                .name(eventName)
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .averageBookingAmount(averageBookingAmount)
                .build();
    }
}