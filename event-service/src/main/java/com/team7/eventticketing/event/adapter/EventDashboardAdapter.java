package com.team7.eventticketing.event.adapter;

import com.team7.eventticketing.event.dto.EventDashboardDTO;

/**
 * Adapter Pattern — converts a raw Object[] row returned by
 * findEventDashboardMetrics() into a typed EventDashboardDTO.
 *
 * Row layout (must match the native SQL query column order):
 *   row[0] = totalBookings    (Number)
 *   row[1] = totalRevenue     (Number)
 *   row[2] = totalTicketsSold (Number)
 *   row[3] = usedTickets      (Number)
 *
 * Called by ObjectArrayDtoAdapter.toEventDashboardDTO()
 * which is called by EventService.getEventDashboardCached()
 */
public class EventDashboardAdapter {

    public EventDashboardDTO adapt(Object[] row, Long eventId,
                                   String name, Double rating) {
        long totalBookings    = row != null && row.length > 0 && row[0] != null
                ? ((Number) row[0]).longValue() : 0L;
        double totalRevenue   = row != null && row.length > 1 && row[1] != null
                ? ((Number) row[1]).doubleValue() : 0.0;
        long totalTicketsSold = row != null && row.length > 2 && row[2] != null
                ? ((Number) row[2]).longValue() : 0L;
        long usedTickets      = row != null && row.length > 3 && row[3] != null
                ? ((Number) row[3]).longValue() : 0L;

        double attendanceRate = totalTicketsSold == 0
                ? 0.0 : (double) usedTickets / totalTicketsSold;

        return EventDashboardDTO.builder()
                .eventId(eventId)
                .name(name)
                .totalBookings(totalBookings)
                .totalTicketsSold(totalTicketsSold)
                .totalRevenue(totalRevenue)
                .averageAttendanceRate(attendanceRate)
                .averageRating(rating == null ? 0.0 : rating)
                .build();
    }
}