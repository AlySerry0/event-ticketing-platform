package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.adapter.ObjectArrayDtoAdapter;
import com.team7.eventticketing.event.dto.EventDashboardDTO;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.repository.EventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventCacheService {

    private final EventRepository eventRepository;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();

    public EventCacheService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }


    /**
     * Inner cached method — only the DB computation is cached, not the Mongo log.
     * Never call this directly from the controller; call getEventDashboard() instead.
     */
    @Cacheable(value = "S2-F12", key = "#eventId")
    public EventDashboardDTO getEventDashboardCached(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        Object[] row = eventRepository.findEventDashboardMetrics(eventId);

        return objectArrayDtoAdapter.toEventDashboardDTO(
                row,
                event.getId(),
                event.getName(),
                event.getRating()
        );
    }
}