package com.team7.eventticketing.event.adapter;

import com.team7.eventticketing.event.model.EventActivityEvent;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Adapter Pattern — converts a raw MongoDB {@link Document} (or
 * {@link EventActivityEvent}) into a generic Map or a typed DTO.
 *
 * S2 usage: S1-F12 activity feed, S2-F12 dashboard logging reads.
 * Whoever implements S2-F10 / S2-F12 can call adaptToMap() to get a
 * plain Map representation of any MongoDB document, or add a new
 * typed adapt() overload below following the same pattern.
 *
 * Pattern:
 *   MongoDocumentAdapter adapter = new MongoDocumentAdapter();
 *   Map<String, Object> result   = adapter.adaptToMap(mongoDocument);
 */
public class MongoDocumentAdapter {

    /**
     * Converts a raw MongoDB {@link Document} to a plain {@code Map<String, Object>}.
     * Useful when the caller just needs key-value access without a typed DTO.
     *
     * @param document raw MongoDB Document from a query result
     * @return Map representation of the document (never null)
     */
    public Map<String, Object> adaptToMap(Document document) {
        if (document == null) return Map.of();
        return Map.copyOf(document);
    }

    /**
     * Converts a persisted {@link EventActivityEvent} entity (already deserialized
     * by Spring Data) into a generic map — useful for audit trail endpoints that
     * return raw event data without a dedicated DTO.
     *
     * @param event the EventActivityEvent retrieved from MongoDB via Spring Data
     * @return Map with keys: id, eventId, action, timestamp, details
     */
    public Map<String, Object> adaptEventActivityToMap(EventActivityEvent event) {
        if (event == null) return Map.of();

        return Map.of(
                "id",        event.getId()        != null ? event.getId()        : "",
                "eventId",   event.getEventId()   != null ? event.getEventId()   : 0L,
                "action",    event.getAction()    != null ? event.getAction()    : "",
                "timestamp", event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.MIN,
                "details",   event.getDetails()   != null ? event.getDetails()   : Map.of()
        );
    }

    // -----------------------------------------------------------------------
    // Extension point for S2-F12 implementor:
    //
    // Add a typed adapt method here when you have a dashboard DTO, e.g.:
    //
    //   public ActivityFeedItemDTO adapt(EventActivityEvent event) {
    //       return ActivityFeedItemDTO.builder()
    //           .action(event.getAction())
    //           .timestamp(event.getTimestamp())
    //           .details(event.getDetails())
    //           .build();
    //   }
    // -----------------------------------------------------------------------
}