package com.team7.eventticketing.event.model;

import com.team7.eventticketing.event.observer.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB document for event service activity logs.
 * Collection: event_events
 *
 * Named EventActivityEvent (not EventEvent) to avoid confusion with
 * the domain Event JPA entity.
 *
 * action primary values:
 *   INDEXED, DASHBOARD_VIEWED, DETAILS_UPDATED, STATUS_CHANGED,
 *   RATED, SESSION_VERIFIED, EVENT_CREATED, EVENT_UPDATED, EVENT_DELETED
 */
@Document(collection = "event_events")
public class EventActivityEvent implements MongoEvent {

    @Id
    private String id;

    private Long eventId;

    private String action;

    private LocalDateTime timestamp;

    private Map<String, Object> details;

    public EventActivityEvent() {
    }

    public EventActivityEvent(Long eventId, String action,
                              LocalDateTime timestamp, Map<String, Object> details) {
        this.eventId = eventId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    @Override
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public Long getEventId() { return eventId; }

    public void setEventId(Long eventId) { this.eventId = eventId; }

    @Override
    public String getAction() { return action; }

    public void setAction(String action) { this.action = action; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    public void setDetails(Map<String, Object> details) { this.details = details; }
}