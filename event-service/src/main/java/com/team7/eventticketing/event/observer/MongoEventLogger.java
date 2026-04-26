package com.team7.eventticketing.event.observer;

import com.team7.eventticketing.event.model.EventActivityEvent;
import com.team7.eventticketing.event.repository.EventActivityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Observer Pattern — Concrete observer for the event-service.
 *
 * On every onEvent() call:
 *   1. Builds factory params from the action string and payload
 *   2. Calls EventFactory.createEvent(EVENT_ACTIVITY, params)
 *   3. Persists the resulting EventActivityEvent to MongoDB
 *
 * Failure policy: Any MongoDB exception is caught, logged at WARN level,
 * and NOT rethrown — the upstream PostgreSQL transaction must never roll back
 * due to a Mongo write failure.
 *
 * This class is a Spring @Component so it can be auto-wired with the
 * MongoDB repository, but the Observer chain itself is classical GoF
 * (register/unregister/notifyObservers on the subject).
 */
@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    // Bound EventType for event-service
    private static final EventType BOUND_TYPE = EventType.EVENT_ACTIVITY;

    private final EventActivityEventRepository repository;

    public MongoEventLogger(EventActivityEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Receives a state-change notification from the subject.
     *
     * @param action  action identifier, e.g. "EVENT_CREATED", "STATUS_CHANGED"
     * @param payload map with keys: eventId (Long), details (Map<String,Object>)
     *                OR any object — the logger extracts what it needs safely
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(String action, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("action", action);

            if (payload instanceof Map<?, ?> map) {
                Object eventId = map.get("eventId");
                if (eventId != null) {
                    params.put("eventId", ((Number) eventId).longValue());
                }
                // Everything in the payload map except "eventId" becomes details
                Map<String, Object> details = new HashMap<>((Map<String, Object>) map);
                details.remove("eventId");
                params.put("details", details);
            } else {
                // Payload is not a map — store a minimal details entry
                Map<String, Object> details = new HashMap<>();
                if (payload != null) {
                    details.put("payload", payload.toString());
                }
                params.put("details", details);
            }

            MongoEvent event = EventFactory.createEvent(BOUND_TYPE, params);
            repository.save((EventActivityEvent) event);

        } catch (Exception ex) {
            // Soft-dependency: never let a Mongo failure bubble up
            log.warn("MongoEventLogger failed to persist event [action={}]: {}",
                    action, ex.getMessage(), ex);
        }
    }
}