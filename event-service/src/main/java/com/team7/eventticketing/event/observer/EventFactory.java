package com.team7.eventticketing.event.observer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Factory Pattern — creates the correct MongoEvent concrete subtype
 * based on the EventType enum value.
 *
 * The event-service's MongoEventLogger is bound to EVENT_ACTIVITY,
 * so it always calls createEvent(EVENT_ACTIVITY, params).
 *
 * Required params keys (all types):
 *   "action"    — String, the action identifier (e.g. "EVENT_CREATED")
 *   "eventId"   — Long, for EVENT_ACTIVITY type
 *   "details"   — Map<String, Object>, optional additional context
 */
public class EventFactory {

    private EventFactory() {
        // Utility class — not instantiated
    }

    /**
     * Creates a MongoEvent of the appropriate concrete subtype.
     *
     * @param type   which service's event type to create
     * @param params map containing action, entity ID, and details
     * @return concrete MongoEvent instance typed as MongoEvent
     */
    @SuppressWarnings("unchecked")
    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type == null) {
            throw new IllegalArgumentException("EventType must not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }

        String action = (String) params.get("action");
        Map<String, Object> details = (Map<String, Object>) params.get("details");
        LocalDateTime timestamp = LocalDateTime.now();

        switch (type) {
            case EVENT_ACTIVITY -> {
                Long eventId = params.get("eventId") != null
                        ? ((Number) params.get("eventId")).longValue()
                        : null;
                EventActivityEvent event = new EventActivityEvent(eventId, action, timestamp, details);
                return event;
            }
            default -> throw new IllegalArgumentException(
                    "EventFactory in event-service only handles EVENT_ACTIVITY. Got: " + type);
        }
    }
}