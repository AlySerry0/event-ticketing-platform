package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.elasticsearch.EventSearchDocument;
import com.team7.eventticketing.event.observer.EntityObserver;
import com.team7.eventticketing.event.repository.EventSearchRepository;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.observer.MongoEventLogger;
import com.team7.eventticketing.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class EventIndexService {

    private final EventRepository eventRepository;          // PG
    private final EventSearchRepository searchRepository;   // ES
    private static final Logger log = LoggerFactory.getLogger(EventIndexService.class);

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public void register(EntityObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(action, payload);
        }
    }
    // constructor injection...
    public EventIndexService(EventRepository eventRepository,
                             EventSearchRepository searchRepository,
                             MongoEventLogger mongoEventLogger) {
        this.eventRepository = eventRepository;
        this.searchRepository = searchRepository;

        this.register(mongoEventLogger);
    }


    public void indexEvent(Long eventId, String source) {
        // 1. PG lookup — hard dependency, let 404 propagate
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Event not found"));

        // 2. Build description from JSONB — default empty string if missing
        String description = "";
        if (event.getDetails() != null && event.getDetails().get("description") != null) {
            description = event.getDetails().get("description").toString();
        }

        // 3. Build ES document
        EventSearchDocument doc = new EventSearchDocument(
                event.getId(), event.getName(), event.getCategory().name(),
                event.getVenue(), description, event.getEventDate(),
                event.getRating(), event.getStatus().name()
        );

        // 4. ES save — soft dependency, try-catch here
        try {
            searchRepository.save(doc);
        } catch (Exception ex) {
            log.warn("Elasticsearch indexing failed for eventId={}: {}", eventId, ex.getMessage());
        }

        // 5. MongoDB log — via MongoEventLogger which already has its OWN
        //    try-catch internally, so no wrapping needed here
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("indexedFields", List.of("name","category","venue",
                "description","eventDate","rating","status"));
        payload.put("source", source);
        notifyObservers("INDEXED", payload);    }

    public void removeFromIndex(Long eventId, String eventName) {
        // ES remove — soft dependency only
        try {
            searchRepository.deleteById(eventId);
        } catch (Exception ex) {
            log.warn("Elasticsearch delete failed for eventId={}: {}", eventId, ex.getMessage());
        }
        // NO mongoEventLogger call here — EventService.deleteEvent() handles that
    }
}