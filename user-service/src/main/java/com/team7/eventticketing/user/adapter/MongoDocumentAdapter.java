package com.team7.eventticketing.user.adapter;

import com.team7.eventticketing.user.dto.ActivityEventDTO;
import com.team7.eventticketing.user.model.AuthEvent;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Adapter Pattern (PDF §3.8) — converts raw MongoDB sources into typed DTOs.
 *
 * S1 usage: AuthEvent documents → ActivityEventDTO for the S1-F12 activity feed.
 *
 * Pattern:
 *   MongoDocumentAdapter adapter = new MongoDocumentAdapter();
 *   ActivityEventDTO dto = adapter.adapt(authEvent);
 */
public class MongoDocumentAdapter {

    /**
     * Converts a persisted {@link AuthEvent} entity (already deserialized by Spring
     * Data MongoDB) into the typed {@link ActivityEventDTO}.
     *
     * @param event the AuthEvent retrieved from MongoDB
     * @return ActivityEventDTO with action, timestamp and details
     */
    public ActivityEventDTO adapt(AuthEvent event) {
        if (event == null) {
            return new ActivityEventDTO(null, null, Map.of());
        }
        return new ActivityEventDTO(
                event.getAction(),
                event.getTimestamp(),
                event.getDetails() != null ? event.getDetails() : Map.of()
        );
    }

    /**
     * Converts a raw MongoDB {@link Document} to a plain {@code Map<String, Object>}.
     * Useful when callers need key-value access without a typed DTO (e.g., audit
     * trail dumps).
     *
     * @param document raw MongoDB Document from a query result
     * @return Map representation of the document (never null)
     */
    public Map<String, Object> adaptToMap(Document document) {
        if (document == null) return Map.of();
        return Map.copyOf(document);
    }

    /**
     * Converts a typed AuthEvent into a generic Map — useful for endpoints that
     * dump raw event data without a dedicated DTO.
     *
     * @param event AuthEvent retrieved via Spring Data
     * @return Map with keys: id, userId, action, timestamp, details
     */
    public Map<String, Object> adaptAuthEventToMap(AuthEvent event) {
        if (event == null) return Map.of();
        return Map.of(
                "id",        event.getId()        != null ? event.getId()        : "",
                "userId",    event.getUserId()    != null ? event.getUserId()    : 0L,
                "action",    event.getAction()    != null ? event.getAction()    : "",
                "timestamp", event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.MIN,
                "details",   event.getDetails()   != null ? event.getDetails()   : Map.of()
        );
    }
}