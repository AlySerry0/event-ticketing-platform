package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.dto.EventDashboardDTO;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.repository.EventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EventDashboardCacheService {

    private final EventRepository eventRepository;

    public EventDashboardCacheService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Cacheable(value = "S2-F12", key = "#eventId")
    public EventDashboardDTO getDashboard(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        Object[] row = eventRepository.findEventDashboardMetrics(eventId);
        long totalBookings = getLongValue(row, 0);
        double totalRevenue = getDoubleValue(row, 1);
        long totalTicketsSold = getLongValue(row, 2);
        long usedTickets = getLongValue(row, 3);
        double averageAttendanceRate = totalTicketsSold == 0
                ? 0.0
                : (double) usedTickets / totalTicketsSold;

        return EventDashboardDTO.builder()
                .eventId(event.getId())
                .name(event.getName())
                .totalBookings(totalBookings)
                .totalTicketsSold(totalTicketsSold)
                .totalRevenue(totalRevenue)
                .averageAttendanceRate(averageAttendanceRate)
                .averageRating(event.getRating() == null ? 0.0 : event.getRating())
                .build();
    }

    private long getLongValue(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0L;
        }
        return ((Number) row[index]).longValue();
    }

    private double getDoubleValue(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0.0;
        }
        return ((Number) row[index]).doubleValue();
    }
}
